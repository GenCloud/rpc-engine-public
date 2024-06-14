package org.genfork.rpc.lock;

import org.genfork.rpc.Destroyable;

import java.util.concurrent.CompletableFuture;

/**
 * @author: GenCloud
 * @date: 2023/01
 */
public interface Lock extends Destroyable {
	CompletableFuture<Boolean> tryLockAsync(long threadId);

	CompletableFuture<Boolean> tryLockAsync(long threadId, long waitTime);

	CompletableFuture<Boolean> tryLockAsync(long threadId, long waitTime, long leaseTime);

	CompletableFuture<Boolean> isHeldBy(long threadId);

	CompletableFuture<Void> unlockAsync(long threadId);
}
