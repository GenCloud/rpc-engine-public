package org.genfork.rpc.data;

import io.micrometer.core.instrument.MeterRegistry;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.genfork.rpc.SyncServiceMethodMetadata;

import java.lang.reflect.Method;

/**
 * @author: GenCloud
 * @date: 2023/01
 */
@Data
@NoArgsConstructor
public class DefaultRequest implements RequestMessage {
	private String requestId;
	private Class<?> serviceClass;
	private String methodName;

	private Object[] args;
	private transient boolean voidType;

	private boolean noResult;

	private long deadline = -1;

	public DefaultRequest(String requestId, Class<?> serviceClass, Method method, Object[] args, boolean voidType) {
		this.requestId = requestId;
		this.serviceClass = serviceClass;
		this.methodName = method.getName();
		this.args = args;
		this.voidType = voidType;
	}

	public Object invoke(MeterRegistry meterRegistry, Object service) throws Throwable {
		return SyncServiceMethodMetadata.invokeCached(meterRegistry, service, methodName, args);
	}
}
