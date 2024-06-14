package org.genfork.rpc.util;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

/**
 * @author: GenCloud
 * @date: 2023/01
 */
public class ChannelSupplier<T> implements Supplier<T> {
	private final AtomicInteger counter = new AtomicInteger();
	private final T[] elements;

	public ChannelSupplier(T[] elements) {
		this.elements = elements;
	}

	public T[] getAll() {
		return elements;
	}

	@Override
	public T get() {
		final int i = counter.incrementAndGet();
		return elements[i % elements.length];
	}
}
