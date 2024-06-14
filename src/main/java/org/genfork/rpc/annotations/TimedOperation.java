package org.genfork.rpc.annotations;

import org.apache.commons.lang3.StringUtils;

import java.lang.annotation.*;

/**
 * @author: GenCloud
 * @date: 2023/02
 */
@Target({ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Inherited
@Documented
public @interface TimedOperation {
	String description() default StringUtils.EMPTY;

	String operation() default StringUtils.EMPTY;
}
