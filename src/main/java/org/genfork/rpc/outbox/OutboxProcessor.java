package org.genfork.rpc.outbox;

import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;

import java.util.concurrent.CompletableFuture;

/**
 * @author: GenCloud
 * @date: 2023/11
 */
public interface OutboxProcessor<T> {
	T openTransaction();

	void finishTransaction(T txContext, boolean error);

	@NonNull
	CompletableFuture<Void> fail(String requestId, String serviceName, String serviceClass, byte[] requestData);

	@Nullable
	OutboxRecordInfo next(T txContext);

	void delete(T txContext, String requestId);
}
