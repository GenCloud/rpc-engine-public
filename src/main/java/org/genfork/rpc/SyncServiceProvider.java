package org.genfork.rpc;

import lombok.Getter;
import org.cliffc.high_scale_lib.NonBlockingHashMap;

import java.util.Map;

/**
 * @author: GenCloud
 * @date: 2023/01
 */
public class SyncServiceProvider {
	@Getter(lazy = true)
	private static final SyncServiceProvider instance = new SyncServiceProvider();

	private final Map<Class<?>, Object> serviceMap = new NonBlockingHashMap<>();

	public Object getService(Class<?> serviceClass) {
		return serviceMap.get(serviceClass);
	}

	public void bind(Class<?> serviceClass, Object remoteService) {
		serviceMap.computeIfAbsent(
				serviceClass,
				v -> remoteService
		);
	}
}
