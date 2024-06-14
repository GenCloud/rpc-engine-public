package org.genfork.rpc.outbox;

import org.genfork.rpc.KryoCodec;
import org.genfork.rpc.data.DefaultRequest;

import java.util.concurrent.CompletableFuture;

/**
 * @author: GenCloud
 * @date: 2023/11
 */
@SuppressWarnings({"unchecked", "rawtypes"})
public class OutboxProcessorInformation {
	private final OutboxProcessor outboxProcessor;
	private final Class<? extends Throwable>[] catchOn;

	public OutboxProcessorInformation(OutboxProcessor outboxProcessor, Class<? extends Throwable>[] catchOn) {
		this.outboxProcessor = outboxProcessor;
		this.catchOn = catchOn;
	}

	public CompletableFuture<Void> processFail(String requestId, String serviceName, Class<?> serviceClass, DefaultRequest request) {
		final byte[] requestData = KryoCodec.write(request);
		final String serviceClassName = serviceClass.getName();
		return outboxProcessor.fail(requestId, serviceName, serviceClassName, requestData);
	}

	public boolean isNeedCatch(Throwable error) {
		if (catchOn == null || catchOn.length == 0) {
			return true;
		}

		final Class<? extends Throwable> errorClass = error.getClass();
		for (Class<? extends Throwable> catchClass : catchOn) {
			if (catchClass == errorClass || catchClass.isAssignableFrom(errorClass)) {
				return true;
			}
		}

		return false;
	}
}
