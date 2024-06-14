package org.genfork.rpc.annotations;

import org.genfork.rpc.outbox.OutboxProcessor;

import java.lang.annotation.*;

/**
 * @author: GenCloud
 * @date: 2023/11
 */
@Target({ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Inherited
@Documented
public @interface Outbox {
	Class<? extends OutboxProcessor<?>> processor();

	Class<? extends Throwable>[] catchOn() default {};
}
