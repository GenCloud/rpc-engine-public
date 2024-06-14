package org.genfork.rpc.context.proxy;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.genfork.rpc.annotations.ConnectableSyncService;
import org.springframework.beans.factory.annotation.AnnotatedBeanDefinition;
import org.springframework.beans.factory.config.AutowireCapableBeanFactory;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.BeanDefinitionHolder;
import org.springframework.beans.factory.support.AbstractBeanDefinition;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.BeanDefinitionReaderUtils;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.EnvironmentAware;
import org.springframework.context.ResourceLoaderAware;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.ImportBeanDefinitionRegistrar;
import org.springframework.core.env.Environment;
import org.springframework.core.io.ResourceLoader;
import org.springframework.core.type.AnnotationMetadata;
import org.springframework.core.type.ClassMetadata;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.lang.NonNull;
import org.springframework.util.ClassUtils;

import java.util.*;
import java.util.stream.Collectors;

/**
 * @author: GenCloud
 * @date: 2024/01
 */
@Slf4j
public class SyncServiceRegistrar implements ImportBeanDefinitionRegistrar, ResourceLoaderAware, EnvironmentAware {
	private ResourceLoader resourceLoader;
	private Environment environment;

	@Override
	public void setResourceLoader(@NonNull ResourceLoader resourceLoader) {
		this.resourceLoader = resourceLoader;
	}

	@Override
	public void setEnvironment(@NonNull Environment environment) {
		this.environment = environment;
	}

	@Override
	public void registerBeanDefinitions(@NonNull AnnotationMetadata metadata, @NonNull BeanDefinitionRegistry registry) {
		searchAndRegisterStaticServices(metadata, registry);
	}

	private void searchAndRegisterStaticServices(AnnotationMetadata metadata, BeanDefinitionRegistry registry) {
		final ClassPathScanningCandidateComponentProvider scanner = getScanner();
		scanner.setResourceLoader(resourceLoader);

		final Set<String> basePackages = getBasePackages(metadata);

		final AnnotationTypeFilter annotationTypeFilter = new AnnotationTypeFilter(ConnectableSyncService.class);
		scanner.addIncludeFilter(annotationTypeFilter);

		basePackages
				.stream()
				.map(scanner::findCandidateComponents)
				.flatMap(Collection::stream)
				.filter(candidateComponent -> candidateComponent instanceof AnnotatedBeanDefinition)
				.map(candidateComponent -> (AnnotatedBeanDefinition) candidateComponent)
				.map(AnnotatedBeanDefinition::getMetadata)
				.filter(ClassMetadata::isInterface)
				.distinct()
				.forEach(annotationMetadata -> tryBuildService(annotationMetadata, registry));
	}

	private void tryBuildService(AnnotationMetadata annotationMetadata, BeanDefinitionRegistry registry) {
		final Map<String, Object> annotationAttributes = annotationMetadata.getAnnotationAttributes(ConnectableSyncService.class.getName());
		final Object serviceAttrValue = Objects.requireNonNullElseGet(annotationAttributes, Collections::emptyMap).get("service");

		final String className = annotationMetadata.getClassName();

		try {
			final Class<?> type = Class.forName(className);

			final BeanDefinitionBuilder beanDefinitionBuilder = BeanDefinitionBuilder.genericBeanDefinition(SyncServiceFactoryBean.class);
			beanDefinitionBuilder.addPropertyValue("type", className);
			beanDefinitionBuilder.addPropertyValue("serviceName", serviceAttrValue instanceof String serviceName ? serviceName : null);
			beanDefinitionBuilder.setLazyInit(true);

			final AbstractBeanDefinition beanDefinition = beanDefinitionBuilder.getBeanDefinition();

			final BeanDefinitionHolder holder = new BeanDefinitionHolder(beanDefinition, className, new String[]{type.getSimpleName()});
			BeanDefinitionReaderUtils.registerBeanDefinition(holder, registry);
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	@SuppressWarnings("rawtypes")
	private Set<String> getBasePackages(AnnotationMetadata importingClassMetadata) {
		Map<String, Object> attributes = importingClassMetadata.getAnnotationAttributes(SpringBootApplication.class.getCanonicalName());
		if (attributes == null) {
			attributes = importingClassMetadata.getAnnotationAttributes(ComponentScan.class.getCanonicalName());
		}

		Set<String> basePackages = new HashSet<>();
		if (attributes != null) {
			basePackages = Arrays.stream((String[]) attributes.get("scanBasePackages")).filter(StringUtils::isNotEmpty).collect(Collectors.toSet());

			Arrays.stream((Class[]) attributes.get("scanBasePackageClasses")).map(ClassUtils::getPackageName).forEach(basePackages::add);
		}

		if (basePackages.isEmpty()) {
			basePackages.add(ClassUtils.getPackageName(importingClassMetadata.getClassName()));
		}

		return basePackages;
	}

	private ClassPathScanningCandidateComponentProvider getScanner() {
		return new ClassPathScanningCandidateComponentProvider(false, environment) {
			@Override
			protected boolean isCandidateComponent(@NonNull AnnotatedBeanDefinition beanDefinition) {
				boolean isCandidate = false;
				if (beanDefinition.getMetadata().isIndependent()) {
					if (!beanDefinition.getMetadata().isAnnotation()) {
						isCandidate = true;
					}
				}
				return isCandidate;
			}
		};
	}
}
