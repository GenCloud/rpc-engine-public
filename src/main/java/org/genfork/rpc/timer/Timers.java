package org.genfork.rpc.timer;

import com.lmax.disruptor.RingBuffer;
import org.genfork.rpc.Destroyable;
import org.springframework.util.Assert;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.Set;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

/**
 * @author: GenCloud
 * @date: 2022/10
 */
public class Timers {
    public static WheelTimer create(String name, int resolution) {
        return new WheelTimer(name, resolution, WheelTimer.DEFAULT_WHEEL_SIZE, new WheelTimer.SleepWait());
    }

    public static Supplier<WheelTimer> create(int timerCount, String name, int resolution) {
        final Executor executor = Executors.newThreadPerTaskExecutor(
                Thread.ofVirtual().name(name + "-", 0).factory()
        );

        final WheelTimer[] array = new WheelTimer[timerCount];
        for(int i = 0; i < timerCount; i++) {
            array[i] = new WheelTimer(resolution, WheelTimer.DEFAULT_WHEEL_SIZE, new WheelTimer.SleepWait(), executor);
        }

        return new RoundRobinSupplier(array);
    }

    public static class RoundRobinSupplier implements Supplier<WheelTimer>, Destroyable {
        private final AtomicInteger counter = new AtomicInteger();
        private final WheelTimer[] timers;

        public RoundRobinSupplier(WheelTimer[] timers) {
            this.timers = timers;
        }

        @Override
        public WheelTimer get() {
            int i = counter.incrementAndGet();
            if (i >= Integer.MAX_VALUE - 1_000_000) {
                counter.set(0);
                i = 0;
            }

            return timers[i % timers.length];
        }

        @Override
        public void destroy() {
            for (WheelTimer timer : timers) {
                timer.cancel();
            }
        }
    }

    public static class WheelTimer {
        public static final int DEFAULT_WHEEL_SIZE = 32;

        private final RingBuffer<Set<TimerPausable>> wheel;
        private final int resolution;
        private final Thread loop;
        private final Executor executor;
        private final WaitStrategy waitStrategy;

        public WheelTimer(String name, int resolution, int wheelSize, WaitStrategy waitStrategy) {
            this(resolution, wheelSize, waitStrategy, Executors.newThreadPerTaskExecutor(
                    Thread.ofVirtual().name(name + "-", 0).factory()));
        }

        /**
         * Create a new {@code WheelTimer} using the given timer {@param resolution} and {@param wheelSize}. All times
         * will
         * rounded up to the closest multiple of this resolution.
         *
         * @param resolution   resolution of this timer in milliseconds
         * @param wheelSize    size of the Ring Buffer supporting the Timer, the larger the wheel, the less the lookup time is
         *                     for sparse timeouts. Sane default is 512.
         * @param waitStrategy strategy for waiting for the next tick
         * @param executor     Executor instance to submit tasks to
         */
        public WheelTimer(int resolution, int wheelSize, WaitStrategy waitStrategy, Executor executor) {
            this.waitStrategy = waitStrategy;
            this.resolution = resolution;

            wheel = RingBuffer.createMultiProducer(ConcurrentSkipListSet::new, wheelSize);

            loop = Thread.ofVirtual().factory().newThread(new TimerProcessor());

            this.executor = executor;

            start();
        }

        public TimerPausable submit(Runnable action, long period) {
            Assert.isTrue(!loop.isInterrupted(), "Cannot submit tasks to this timer as it has been cancelled.");
            return schedule(period, action);
        }

        private TimerPausable schedule(long delay, Runnable action) {
            final long firstFireOffset = delay / resolution;
            final long firstFireRounds = firstFireOffset / wheel.getBufferSize();

            final TimerPausable timer = new TimerPausable(firstFireRounds, 0, action, 0, delay);
            timer.cancelAfterUse();

            wheel.get(wheel.getCursor() + firstFireOffset + 1).add(timer);
            return timer;
        }

        /**
         * Reschedule a {@link TimerPausable}  for the next fire
         */
        private void reschedule(TimerPausable registration) {
            TimerPausable.VALUE_rounds.getAndSet(registration, registration.rescheduleRounds);
            wheel.get(wheel.getCursor() + registration.getOffset() + 1).add(registration);
        }

        /**
         * Start the Timer
         */
        public void start() {
            loop.start();
            wheel.publish(0);
        }

        /**
         * Cancel current Timer
         */
        public void cancel() {
            loop.interrupt();
        }

        @Override
        public String toString() {
            return String.format("WheelTimer { Buffer Size: %d, Resolution: %d }", wheel.getBufferSize(), resolution);
        }

        private class TimerProcessor implements Runnable {
            @Override
            public void run() {
                long deadline = System.currentTimeMillis();

                while (true) {
                    final Set<TimerPausable> registrations = wheel.get(wheel.getCursor());

                    for (TimerPausable r : registrations) {
                        if (r.isCancelled()) {
                            registrations.remove(r);
                        } else if (r.ready()) {
                            executor.execute(r);
                            registrations.remove(r);

                            if (!r.isCancelAfterUse()) {
                                reschedule(r);
                            }
                        } else if (r.isPaused()) {
                            reschedule(r);
                        } else {
                            r.decrement();
                        }
                    }

                    deadline += resolution;

                    try {
                        waitStrategy.waitUntil(deadline);
                    } catch (InterruptedException e) {
                        return;
                    }

                    wheel.publish(wheel.next());
                }
            }
        }

        public interface WaitStrategy {

            /**
             * Wait until the given deadline, {@param deadlineMilliseconds}
             *
             * @param deadlineMilliseconds deadline to wait for, in milliseconds
             */
            void waitUntil(long deadlineMilliseconds) throws InterruptedException;
        }

        /**
         * Yielding wait strategy.
         * Spins in the loop, until the deadline is reached. Releases the flow control
         * by means of Thread.yield() call. This strategy is less precise than BusySpin
         * one, but is more scheduler-friendly.
         */
        @SuppressWarnings("unused")
        public static class YieldingWait implements WaitStrategy {

            @Override
            public void waitUntil(long deadlineMilliseconds) throws InterruptedException {
                while (deadlineMilliseconds >= System.currentTimeMillis()) {
                    Thread.yield();
                    if (Thread.currentThread().isInterrupted()) {
                        throw new InterruptedException();
                    }
                }
            }
        }

        /**
         * BusySpin wait strategy.
         * Spins in the loop until the deadline is reached. In a multi-core environment,
         * will occupy an entire core. Is more precise than Sleep wait strategy, but
         * consumes more resources.
         */
        @SuppressWarnings("unused")
        public static class BusySpinWait implements WaitStrategy {

            @Override
            public void waitUntil(long deadlineMilliseconds) throws InterruptedException {
                while (deadlineMilliseconds >= System.currentTimeMillis()) {
                    if (Thread.currentThread().isInterrupted()) {
                        throw new InterruptedException();
                    }
                }
            }
        }

        /**
         * Sleep wait strategy.
         * Will release the flow control, giving other threads a possibility of execution
         * on the same processor. Uses less resources than BusySpin wait, but is less
         * precise.
         */
        public static class SleepWait implements WaitStrategy {

            @Override
            public void waitUntil(long deadlineMilliseconds) throws InterruptedException {
                long sleepTimeMs = deadlineMilliseconds - System.currentTimeMillis();
                if (sleepTimeMs > 0) {
                    Thread.sleep(sleepTimeMs);
                }
            }
        }

        /**
         * Timer Registration
         */
        @SuppressWarnings("all")
        public static class TimerPausable implements Runnable, Comparable<TimerPausable>, Pausable {
            private static final VarHandle VALUE_rounds;
            private static final VarHandle VALUE_status;
            private static final VarHandle VALUE_cancelAfterUse;

            static {
                try {
                    final MethodHandles.Lookup l = MethodHandles.lookup();
                    VALUE_rounds = l.findVarHandle(TimerPausable.class, "rounds", long.class);
                    VALUE_status = l.findVarHandle(TimerPausable.class, "status", int.class);
                    VALUE_cancelAfterUse = l.findVarHandle(TimerPausable.class, "cancelAfterUse", int.class);
                } catch (ReflectiveOperationException e) {
                    throw new ExceptionInInitializerError(e);
                }
            }

            public static int STATUS_PAUSED = 1, STATUS_CANCELLED = -1, STATUS_READY = 0;

            private final long delay;
            private final Runnable delegate;
            private final long rescheduleRounds, scheduleOffset;
            private volatile long rounds;
            private volatile int status = STATUS_READY;
            private volatile int cancelAfterUse;

            /**
             * Creates a new Timer Registration with given {@param rounds}, {@param offset} and {@param delegate}.
             *
             * @param rounds         amount of rounds the Registration should go through until it's elapsed
             * @param scheduleOffset offset of in the Ring Buffer for rescheduling
             * @param delegate       delegate that will be ran whenever the timer is elapsed
             */
            public TimerPausable(long rounds, long scheduleOffset, Runnable delegate, long rescheduleRounds, long delay) {
                Assert.notNull(delegate, "Delegate cannot be null");

                this.rescheduleRounds = rescheduleRounds;
                this.scheduleOffset = scheduleOffset;
                this.delegate = delegate;
                this.rounds = rounds;
                this.delay = System.currentTimeMillis() + delay;
            }

            /**
             * Decrement an amount of runs Registration has to run until it's elapsed
             */
            public void decrement() {
                VALUE_rounds.getAndAdd(this, -1);
            }

            /**
             * Check whether the current Registration is ready for execution
             *
             * @return whether or not the current Registration is ready for execution
             */
            public boolean ready() {
                final int s = (int) VALUE_status.getVolatile(this);
                final long r = (long) VALUE_rounds.getVolatile(this);
                return s == STATUS_READY && r == 0;
            }

            public long getDelay(TimeUnit unit) {
                final long current = System.currentTimeMillis();
                return unit.convert(delay - current, TimeUnit.MILLISECONDS);
            }

            /**
             * Run the delegate of the current Registration
             */
            @Override
            public void run() {
                delegate.run();
            }

            /**
             * Reset the Registration
             */
            public void reset() {
                VALUE_status.setVolatile(this, STATUS_READY);
                VALUE_rounds.setVolatile(this, rescheduleRounds);
            }

            /**
             * Cancel the registration
             *
             * @return current Registration
             */
            @Override
            public TimerPausable cancel() {
                if (!isCancelled()) {
                    VALUE_status.setVolatile(this, STATUS_CANCELLED);
                }

                return this;
            }

            /**
             * Check whether the current Registration is cancelled
             *
             * @return whether or not the current Registration is cancelled
             */
            public boolean isCancelled() {
                final int s = (int) VALUE_status.getVolatile(this);
                return s == STATUS_CANCELLED;
            }

            /**
             * Pause the current Regisration
             *
             * @return current Registration
             */
            @Override
            public TimerPausable pause() {
                if (!isPaused()) {
                    VALUE_status.setVolatile(this, STATUS_PAUSED);
                }

                return this;
            }

            /**
             * Check whether the current Registration is paused
             *
             * @return whether or not the current Registration is paused
             */
            public boolean isPaused() {
                final int s = (int) VALUE_status.getVolatile(this);
                return s == STATUS_PAUSED;
            }

            /**
             * Resume current Registration
             *
             * @return current Registration
             */
            @Override
            public TimerPausable resume() {
                if (isPaused()) {
                    reset();
                }

                return this;
            }

            /**
             * Cancel this {@link TimerPausable} after it has been selected and used. {@link
             * reactor.core.Dispatcher} implementations should respect this value and perform
             * the cancellation.
             *
             * @return {@literal this}
             */
            public TimerPausable cancelAfterUse() {
                VALUE_cancelAfterUse.setVolatile(this, 1);
                return this;
            }

            public boolean isCancelAfterUse() {
                final int s = (int) VALUE_cancelAfterUse.getVolatile(this);
                return s == 1;
            }

            public long getOffset() {
                return scheduleOffset;
            }

            @Override
            public int compareTo(TimerPausable o) {
                final long l1 = (long) VALUE_rounds.getVolatile(this);
                final long l2 = (long) VALUE_rounds.getVolatile(o);
                if (l1 == l2) {
                    return o == this ? 0 : -1;
                }

                return Long.compare(l1, l2);
            }

            @Override
            public String toString() {
                return String.format("WheelTimer { Rounds left: %d, Status: %d }", (long) VALUE_rounds.getVolatile(this), (int) VALUE_status.getVolatile(this));
            }
        }
    }
}
