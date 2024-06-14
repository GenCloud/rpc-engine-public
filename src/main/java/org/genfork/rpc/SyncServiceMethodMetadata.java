package org.genfork.rpc;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.Getter;
import lombok.experimental.UtilityClass;
import org.apache.commons.lang3.StringUtils;
import org.cliffc.high_scale_lib.NonBlockingHashMap;
import org.genfork.rpc.annotations.TimedOperation;
import org.genfork.rpc.exceptions.ServiceRemoteException;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodHandles.Lookup;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * @author: GenCloud
 * @date: 2023/02
 */
@UtilityClass
public class SyncServiceMethodMetadata {
	private static final Map<Class<?>, Map<String, MethodMetadata>> CLASS_METADATA = new NonBlockingHashMap<>();

	private static final Lookup LOOKUP = MethodHandles.publicLookup();

	public Object invokeCached(MeterRegistry meterRegistry, Object service, String methodName, Object[] args) throws Throwable {
		final Class<?> serviceClass = service.getClass();

		final Map<String, MethodMetadata> classMap = CLASS_METADATA.computeIfAbsent(serviceClass, v -> new NonBlockingHashMap<>());

		final MethodMetadata metadata = classMap.computeIfAbsent(methodName, v -> {
			try {
				final Method method = findMethod(methodName, args, serviceClass);
				return new MethodMetadata(meterRegistry, method, LOOKUP.unreflect(method));
			} catch (NoSuchMethodException | IllegalAccessException e) {
				throw new ServiceRemoteException(e.getMessage());
			}
		});

		if (args == null || args.length == 0) {
			return doWithMeterIfPresent(
					meterRegistry,
					metadata,
					() -> metadata.getMethodHandle().invoke(service)
			);
		}

		return doWithMeterIfPresent(
				meterRegistry,
				metadata,
				() -> {
					final Object[] copyWithOwner = new Object[args.length + 1];
					copyWithOwner[0] = service;

					for (int i = 0; i < args.length; i++) {
						final Object arg = args[i];
						copyWithOwner[i + 1] = arg;
					}

					return metadata.getMethodHandle().invokeWithArguments(copyWithOwner);
				}
		);
	}

	private Object doWithMeterIfPresent(MeterRegistry meterRegistry, MethodMetadata metadata, ThrowableSupplier<Object> supplier) throws Throwable {
		if (meterRegistry != null) {
			final Timer.Sample timer = Timer.start(meterRegistry);
			final Object result = supplier.get();
			if (result instanceof CompletableFuture<?> future) {
				future.whenComplete((res, th) -> timer.stop(metadata.getTimer()));
			} else {
				timer.stop(metadata.getTimer());
			}

			return result;
		}

		return supplier.get();
	}

	private static Method findMethod(String methodName, Object[] args, Class<?> serviceClass) throws NoSuchMethodException {
		for (Method method : serviceClass.getDeclaredMethods()) {
			if (method.getName().equals(methodName)) {
				final int parameterCount = method.getParameterCount();
				if (args == null && parameterCount == 0) {
					return method;
				}

				if (args != null && args.length == parameterCount) {
					return method;
				}
			}
		}

		throw new NoSuchMethodException("Method not found - " + serviceClass.getName() + "#" + methodName);
	}

	@FunctionalInterface
	public
	interface ThrowableSupplier<T> {
		T get() throws Throwable;
	}

	public static class MethodMetadata {
		@Getter
		private final MethodHandle methodHandle;

		@Getter
		private Timer timer;

		public MethodMetadata(MeterRegistry meterRegistry, Method method, MethodHandle methodHandle) {
			this.methodHandle = methodHandle;

			if (meterRegistry != null) {
				String timedOperationName, timerOperationDescription;
				if (method.isAnnotationPresent(TimedOperation.class)) {
					final TimedOperation timedOperation = method.getDeclaredAnnotation(TimedOperation.class);
					timedOperationName = StringUtils.isEmpty(timedOperation.operation())
							? "sync-server-operation#" + method.getName()
							: timedOperation.operation();
					timerOperationDescription = timedOperation.description();
				} else {
					timedOperationName = "sync-server-operation#" + method.getName();
					timerOperationDescription = StringUtils.EMPTY;
				}

				timer = Timer.builder(timedOperationName)
						.description(timerOperationDescription)
						.register(meterRegistry);
			}
		}
	}
}
