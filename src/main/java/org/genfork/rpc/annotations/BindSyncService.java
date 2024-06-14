package org.genfork.rpc.annotations;

import java.lang.annotation.*;

/**
 * @author: GenCloud
 * @date: 2023/02
 */
@Target({ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Inherited
@Documented
public @interface BindSyncService {
	Class<?> service();

	/**
	 * The SpEL expression to evaluate. Expression should return {@code true} if the
	 * condition passes or {@code false} if it fails.
	 * @return the SpEL expression
	 */
	String condition() default "#{true}";
}
