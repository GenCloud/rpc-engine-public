package org.genfork.rpc.context;

import io.micrometer.core.instrument.MeterRegistry;
import org.genfork.rpc.context.SyncConfigurationProperties.Resources;
import org.genfork.rpc.context.proxy.SyncServicesManagement;
import org.genfork.rpc.lock.LockRegistry;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Role;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

/**
 * @author: GenCloud
 * @date: 2023/02
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(SyncConfigurationProperties.class)
public class SyncServicesAutoConfiguration {
	@Bean
	@Role(BeanDefinition.ROLE_SUPPORT)
	public LockRegistry lockRegistry(ObjectProvider<MeterRegistry> meterRegistry,
	                                 SyncConfigurationProperties syncConfigurationProperties
	) {
		final Resources resources = syncConfigurationProperties.getResources();
		final long lockLeaseTimeout = resources.getLockLeaseTimeout();
		final ThreadFactory factory = Thread.ofVirtual().name("rpc-locks-", 0).factory();
		final ExecutorService executor = Executors.newThreadPerTaskExecutor(factory);
		return new LockRegistry(meterRegistry.getIfAvailable(), executor, lockLeaseTimeout);
	}

	@Bean
	@Role(BeanDefinition.ROLE_SUPPORT)
	public SyncServicesManagement syncServerStarter(ObjectProvider<MeterRegistry> meterRegistry,
	                                                LockRegistry lockRegistry,
	                                                SyncConfigurationProperties syncConfigurationProperties,
	                                                ApplicationEventPublisher applicationEventPublisher) {
		return new SyncServicesManagement(
				syncConfigurationProperties,
				lockRegistry,
				meterRegistry.getIfAvailable(),
				applicationEventPublisher
		);
	}
}
