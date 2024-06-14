package org.genfork.rpc.outbox;

import org.genfork.rpc.KryoCodec;
import org.genfork.rpc.data.DefaultRequest;

/**
 * @author: GenCloud
 * @date: 2023/11
 */
public record OutboxRecordInfo(String serviceName, Class<?> nestedServiceClass, DefaultRequest defaultRequest) {
	public static OutboxRecordInfo from(String serviceName, String serviceClass, byte[] requestData) throws ClassNotFoundException {
		final DefaultRequest defaultRequest = KryoCodec.read(requestData, DefaultRequest.class);
		final Class<?> nestedServiceClass = Class.forName(serviceClass);
		return new OutboxRecordInfo(serviceName, nestedServiceClass, defaultRequest);
	}
}
