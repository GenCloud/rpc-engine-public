package org.genfork.rpc.annotations;

import org.genfork.rpc.context.proxy.SyncServiceRegistrar;
import org.springframework.context.annotation.Import;

import java.lang.annotation.*;

/**
 * @author: GenCloud
 * @date: 2023/02
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Import(SyncServiceRegistrar.class)
public @interface EnableSyncServices {
}
