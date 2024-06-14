package org.genfork.rpc;

import lombok.experimental.UtilityClass;
import org.genfork.rpc.timer.Timers;
import org.genfork.rpc.timer.Timers.WheelTimer;

import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

/**
 * @author: GenCloud
 * @date: 2023/01
 */
@UtilityClass
public class TimeoutTimer {
	private static final Supplier<WheelTimer> timer;

	static {
		timer = Timers.create(2, TimeoutTimer.class.getSimpleName(), 512);
	}

	public static WheelTimer getTimer() {
		return timer.get();
	}
}
