package org.genfork.rpc.context.proxy;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.cglib.proxy.Enhancer;
import org.springframework.cglib.proxy.MethodInterceptor;
import org.springframework.cglib.proxy.MethodProxy;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.lang.NonNull;

import java.lang.reflect.Method;
import java.util.Objects;

/**
 * @author: GenCloud
 * @date: 2024/01
 */
@Slf4j
@ToString
public class SyncServiceFactoryBean implements FactoryBean<Object>, ApplicationContextAware {
	@Getter @Setter private Class<?> type;
	@Getter @Setter private String serviceName;

	private ApplicationContext applicationContext;

	@Override
	public void setApplicationContext(@NonNull ApplicationContext applicationContext) throws BeansException {
		this.applicationContext = applicationContext;
	}

	@Override
	public Class<?> getObjectType() {
		return type;
	}

	@Override
	public Object getObject() {
		final SyncServicesManagement syncServicesManagement = Objects.requireNonNull(
				applicationContext.getBean(SyncServicesManagement.class),
				"'syncServicesManagement' property reference required before context initialization"
		);

		if (StringUtils.isNotEmpty(serviceName)) {
			syncServicesManagement.tryInitConnectableSyncService(serviceName, type);
		} else {
			log.info("Skip sync service [{}] - not allowed to auto creation.", type.getSimpleName());
		}

		return Enhancer.create(type, new Interceptor(type, syncServicesManagement));
	}

	private record Interceptor(Class<?> type, SyncServicesManagement syncServicesManagement) implements MethodInterceptor {
		@Override
			public Object intercept(Object obj, Method method, Object[] args, MethodProxy proxy) throws Throwable {
				return syncServicesManagement.execute(this, type, method, args);
			}
		}
}
