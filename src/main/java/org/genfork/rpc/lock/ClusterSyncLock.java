package org.genfork.rpc.lock;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.genfork.rpc.ClientConnection;
import org.genfork.rpc.data.LockRequest;
import org.genfork.rpc.data.LockRequestType;
import org.genfork.rpc.data.LockRes;
import org.genfork.rpc.util.ChannelSupplier;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * @author: GenCloud
 * @date: 2023/01
 */
@Slf4j
public class ClusterSyncLock implements Lock {
	private final MeterRegistry meterRegistry;
	@Getter
	private final String entry;

	private final long systemLeaseTimeout;
	private final ChannelSupplier<ClientConnection> channels;

	private Timer lockTimer, heldTimer;

	public ClusterSyncLock(MeterRegistry meterRegistry, String entry, ChannelSupplier<ClientConnection> channels, long systemLeaseTimeout) {
		this.meterRegistry = meterRegistry;
		this.entry = entry;
		this.channels = channels;
		this.systemLeaseTimeout = systemLeaseTimeout;

		if (meterRegistry != null) {
			lockTimer = Timer.builder("sync-client-cluster-lock#lock-op")
					.register(meterRegistry);
			heldTimer = Timer.builder("sync-client-cluster-lock#held-op")
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
		final ClientConnection connection = channels.get();
		final String requestId = UUID.randomUUID().toString();
		return doWithMeterIfPresent(
				lockTimer,
				connection
						.async(new LockRequest(requestId, LockRequestType.ACQUIRE, threadId, entry, -1L, systemLeaseTimeout))
						.handle((res, th) -> {
							if (th != null) {
								log.error("Unexpected error while trying acquire lock.", th);
								return false;
							}

							if (res == null) {
								return false;
							}

							final LockRes lockRes = (LockRes) res;
							return lockRes.isSuccess();
						})
		);
	}

	@Override
	public CompletableFuture<Boolean> tryLockAsync(long threadId, long waitTime) {
		final ClientConnection connection = channels.get();
		final String requestId = UUID.randomUUID().toString();
		return doWithMeterIfPresent(
				lockTimer,
				connection
						.async(new LockRequest(requestId, LockRequestType.ACQUIRE, threadId, entry, waitTime, systemLeaseTimeout))
						.handle((res, th) -> {
							if (th != null) {
								log.error("Unexpected error while trying acquire lock.", th);
								return false;
							}

							if (res == null) {
								return false;
							}

							final LockRes lockRes = (LockRes) res;
							return lockRes.isSuccess();
						})
		);
	}

	@Override
	public CompletableFuture<Boolean> tryLockAsync(long threadId, long waitTime, long leaseTime) {
		final ClientConnection connection = channels.get();
		final String requestId = UUID.randomUUID().toString();
		return doWithMeterIfPresent(
				lockTimer,
				connection
						.async(new LockRequest(requestId, LockRequestType.ACQUIRE, threadId, entry, waitTime, leaseTime))
						.handle((res, th) -> {
							if (th != null) {
								log.error("Unexpected error while trying acquire lock.", th);
								return false;
							}

							if (res == null) {
								return false;
							}

							final LockRes lockRes = (LockRes) res;
							return lockRes.isSuccess();
						})
		);
	}

	@Override
	public CompletableFuture<Boolean> isHeldBy(long threadId) {
		final ClientConnection connection = channels.get();
		final String requestId = UUID.randomUUID().toString();
		return doWithMeterIfPresent(
				heldTimer,
				connection
						.async(new LockRequest(requestId, LockRequestType.CHECK, threadId, entry, -1L, systemLeaseTimeout))
						.handle((res, th) -> {
							if (th != null) {
								log.error("Unexpected error while check lock is held by.", th);
								return false;
							}

							if (res == null) {
								return false;
							}

							final LockRes lockRes = (LockRes) res;
							return lockRes.isSuccess();
						})
		);
	}

	@Override
	public CompletableFuture<Void> unlockAsync(long threadId) {
		final ClientConnection connection = channels.get();
		final String requestId = UUID.randomUUID().toString();
		return connection
				.async(new LockRequest(requestId, LockRequestType.RELEASE, threadId, entry, -1L, systemLeaseTimeout))
				.handle((res, th) -> null);
	}

	@Override
	public void destroy() {
		for (ClientConnection connection : channels.getAll()) {
			connection.closeAsync();
		}
	}
}
