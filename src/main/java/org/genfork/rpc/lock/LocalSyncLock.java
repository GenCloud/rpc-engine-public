package org.genfork.rpc.lock;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.genfork.rpc.exceptions.WaitLockTimeoutException;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;

/**
 * @author: GenCloud
 * @date: 2023/02
 */
public class LocalSyncLock implements Lock {
	private static final long EXCLUSIVE_THREAD_ID = Long.MIN_VALUE;

	private final SyncLockValue syncLockValue;
	private final MeterRegistry meterRegistry;
	private final long systemLeaseTimeout;
	private final ExecutorService executor;

	private Timer lockTimer, unlockTimer, heldTimer;

	public LocalSyncLock(SyncLockValue syncLockValue, String entry, MeterRegistry meterRegistry, long systemLeaseTimeout, ExecutorService executor) {
		this.syncLockValue = syncLockValue;
		this.meterRegistry = meterRegistry;
		this.systemLeaseTimeout = systemLeaseTimeout;
		this.executor = executor;

		if (meterRegistry != null) {
			lockTimer = Timer.builder("sync-server-lock#" + entry)
					.register(meterRegistry);
			unlockTimer = Timer.builder("sync-server-unlock#" + entry)
					.register(meterRegistry);
			heldTimer = Timer.builder("sync-server-held#" + entry)
					.register(meterRegistry);
		}
	}

	private <T> CompletableFuture<T> doWithMeterIfPresent(Timer timer, CompletableFuture<T> future) {
		if (meterRegistry != null) {
			final Timer.Sample sampler = Timer.start(meterRegistry);
			future.whenComplete((res, th) -> sampler.stop(timer));
		}

		return future;
	}

	@Override
	public CompletableFuture<Boolean> tryLockAsync(long threadId) {
		return doWithMeterIfPresent(
				lockTimer,
				CompletableFuture.supplyAsync(() ->
						syncLockValue.tryLock(threadId, systemLeaseTimeout), executor)
		);
	}

	@Override
	public CompletableFuture<Boolean> tryLockAsync(long threadId, long waitTime) {
		return tryLockAsync(threadId, waitTime, systemLeaseTimeout);
	}

	@Override
	public CompletableFuture<Boolean> tryLockAsync(long threadId, long waitTime, long leaseTime) {
		if (meterRegistry != null) {
			final Timer.Sample sampler = Timer.start(meterRegistry);
			try {
				final boolean success = syncLockValue.tryLock(threadId, waitTime, leaseTime);
				return CompletableFuture.completedFuture(success);
			} catch (Exception e) {
				return CompletableFuture.failedFuture(e);
			} finally {
				sampler.stop(lockTimer);
			}
		}

		try {
			final boolean success = syncLockValue.tryLock(threadId, waitTime, leaseTime);
			return CompletableFuture.completedFuture(success);
		} catch (Exception e) {
			return CompletableFuture.failedFuture(e);
		}
	}

	@Override
	public CompletableFuture<Boolean> isHeldBy(long threadId) {
		return doWithMeterIfPresent(
				heldTimer,
				CompletableFuture.supplyAsync(() ->
						syncLockValue.isHeldBy(threadId), executor)
		);
	}

	@Override
	public CompletableFuture<Void> unlockAsync(long threadId) {
		if (meterRegistry != null) {
			final Timer.Sample sampler = Timer.start(meterRegistry);

			try {
				syncLockValue.unlock(threadId);
				return CompletableFuture.completedFuture(null);
			} catch (Exception e) {
				return CompletableFuture.failedFuture(e);
			} finally {
				sampler.stop(unlockTimer);
			}
		}

		try {
			syncLockValue.unlock(threadId);
			return CompletableFuture.completedFuture(null);
		} catch (Exception e) {
			return CompletableFuture.failedFuture(e);
		}
	}

	public void exclusiveLock(long leaseTime) {
		try {
			syncLockValue.tryLock(EXCLUSIVE_THREAD_ID, 50, leaseTime);
		} catch (WaitLockTimeoutException e) {
			exclusiveLock(leaseTime);
		}
	}

	public void exclusiveUnlock() {
		try {
			syncLockValue.unlock(EXCLUSIVE_THREAD_ID);
		} catch (Exception e) {
			exclusiveUnlock();
		}
	}

	@Override
	public void destroy() {

	}
}
