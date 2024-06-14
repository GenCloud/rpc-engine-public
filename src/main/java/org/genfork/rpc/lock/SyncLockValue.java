package org.genfork.rpc.lock;

import org.genfork.rpc.exceptions.MonitorNotOwnerException;
import org.genfork.rpc.exceptions.WaitLockTimeoutException;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodHandles.Lookup;
import java.lang.invoke.VarHandle;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.LockSupport;

/**
 * @author: GenCloud
 * @date: 2023/02
 */
public class SyncLockValue {
	public static final LockStatus DEFAULT_STATUS = new LockStatus(-1, -1);

	private static final VarHandle STATUS_HANDLE;

	static {
		try {
			final Lookup lookup = MethodHandles.lookup();
			STATUS_HANDLE = lookup.findVarHandle(SyncLockValue.class, "lockStatus", LockStatus.class);
		} catch (ReflectiveOperationException e) {
			throw new ExceptionInInitializerError(e);
		}
	}

	private final AtomicBoolean inLease = new AtomicBoolean();

	@SuppressWarnings("all")
	private volatile LockStatus lockStatus = DEFAULT_STATUS;

	private LockStatus getStatus() {
		return (LockStatus) STATUS_HANDLE.getVolatile(this);
	}

	public boolean isHeldBy(long threadId) {
		final LockStatus status = getStatus();
		if (status.getLeaseTime() != -1 && System.nanoTime() > status.getLeaseTime()) {
			if (inLease.compareAndSet(false, true)) {
				try {
					if (CAS(-1, -1, status)) {
						return status.getThreadId() != -1 && status.getThreadId() == threadId;
					}
				} finally {
					inLease.set(false);
				}
			}
		}

		return status.getThreadId() != -1 && status.getThreadId() == threadId;
	}

	public boolean tryLock(long threadId, long leaseTime) {
		final LockStatus status = getStatus();
		if (status.getThreadId() == threadId) {
			if (checkLeaseAndTryLock(threadId, leaseTime, status)) {
				return true;
			}

			return true;
		}

		if (status.getThreadId() == -1 && !inLease.get()) {
			return CAS(threadId, leaseTime, status);
		}

		return checkLeaseAndTryLock(threadId, leaseTime, status);
	}

	private boolean checkLeaseAndTryLock(long threadId, long leaseTime, LockStatus status) {
		if (status.getLeaseTime() != -1 && System.nanoTime() > status.getLeaseTime()) {
			if (inLease.compareAndSet(false, true)) {
				try {
					if (CAS(threadId, leaseTime, getStatus())) {
						return true;
					}
				} finally {
					inLease.set(false);
				}
			}
		}

		return false;
	}

	/**
	 * @throws WaitLockTimeoutException if timeout exceeded
	 */
	public boolean tryLock(long threadId, long waitTime, long leaseTime) {
		if (waitTime > 0) {
			final long waitNanos = TimeUnit.MILLISECONDS.toNanos(waitTime) + System.nanoTime();
			for (; ; ) {
				if (tryLock(threadId, leaseTime)) {
					return true;
				}

				final long nanos = waitNanos - System.nanoTime();
				if (nanos > 0L) {
					LockSupport.parkNanos(this, nanos);
				} else {
					throw new WaitLockTimeoutException("Wait time exceeded.");
				}
			}
		}

		return tryLock(threadId, leaseTime);
	}

	/**
	 * @throws RuntimeException if lock already released
	 * @throws MonitorNotOwnerException if release thread not owned this lock
	 */
	public void unlock(long threadId) {
		final LockStatus status = getStatus();
		if (status.getThreadId() != threadId) {
			throw new MonitorNotOwnerException("Current thread not owned this lock.");
		}

		if (!CAS(-1, -1, status)) {
			throw new RuntimeException("Lock already unlocked.");
		}
	}

	private boolean CAS(long threadId, long leaseTime, LockStatus status) {
		return STATUS_HANDLE
				.compareAndSet(
						this,
						status,
						new LockStatus(
								threadId,
								TimeUnit.MILLISECONDS.toNanos(leaseTime) + System.nanoTime()
						)
				);
	}
}
