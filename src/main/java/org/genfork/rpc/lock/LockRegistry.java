package org.genfork.rpc.lock;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.DisposableBean;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * @author: GenCloud
 * @date: 2023/01
 */
public class LockRegistry implements DisposableBean {
	private final Map<String, LocalSyncLock> locks = new ConcurrentHashMap<>();

	private final MeterRegistry meterRegistry;
	private final ExecutorService executor;
	private final long systemLeaseTime;

	public LockRegistry(MeterRegistry meterRegistry, ExecutorService executor, long systemLeaseTime) {
		this.meterRegistry = meterRegistry;
		this.executor = executor;
		this.systemLeaseTime = systemLeaseTime;
	}

	public LocalSyncLock getLock(String key) {
		return locks.computeIfAbsent(key, v -> new LocalSyncLock(new SyncLockValue(), key, meterRegistry, systemLeaseTime, executor));
	}

	@Override
	public void destroy() throws Exception {
		try {
			executor.awaitTermination(60, TimeUnit.SECONDS);
		} catch (Exception e) {
			//
		}

		executor.shutdownNow();
	}
}
