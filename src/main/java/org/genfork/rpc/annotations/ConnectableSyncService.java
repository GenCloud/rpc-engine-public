package org.genfork.rpc.annotations;

import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.lang.annotation.*;

/**
 * @author: GenCloud
 * @date: 2023/02
 */
@Target({ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Inherited
@Documented
public @interface ConnectableSyncService {
	String service() default StringUtils.EMPTY;
}
