package org.genfork.rpc.context.proxy;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.netty.channel.EventLoopGroup;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.cliffc.high_scale_lib.NonBlockingHashMap;
import org.genfork.rpc.*;
import org.genfork.rpc.annotations.*;
import org.genfork.rpc.context.LoopGroupFactory;
import org.genfork.rpc.context.SyncConfigurationProperties;
import org.genfork.rpc.context.SyncConfigurationProperties.Outbound;
import org.genfork.rpc.context.Transport;
import org.genfork.rpc.context.events.AbstractSyncServiceInitEvent;
import org.genfork.rpc.context.events.AbstractSyncServiceInitEvent.ConnectedServiceProperties;
import org.genfork.rpc.context.events.SyncServicesStarted;
import org.genfork.rpc.data.DefaultRequest;
import org.genfork.rpc.lock.ClusterSyncLock;
import org.genfork.rpc.lock.Lock;
import org.genfork.rpc.lock.LockRegistry;
import org.genfork.rpc.outbox.OutboxProcessor;
import org.genfork.rpc.outbox.OutboxProcessorInformation;
import org.genfork.rpc.outbox.OutboxRecordInfo;
import org.genfork.rpc.util.ChannelSupplier;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.config.BeanExpressionContext;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.boot.context.event.ApplicationStartedEvent;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.ApplicationListener;
import org.springframework.context.expression.StandardBeanExpressionResolver;
import org.springframework.core.MethodParameter;
import org.springframework.core.Ordered;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @author: GenCloud
 * @date: 2024/01
 */
@Slf4j
public class SyncServicesManagement implements BeanPostProcessor, InitializingBean, DisposableBean, BeanFactoryAware,
		Ordered, ApplicationListener<ApplicationEvent> {

	private final Map<String, SyncServiceContext> serviceContexts = new ConcurrentHashMap<>(1024);
	private final Map<Class<?>, Map<String, TargetClassMethodMetadata>> methodsMetadata = new ConcurrentHashMap<>(1024);

	private static final Map<Class<?>, OutboxProcessor<?>> outboxProcessors = new ConcurrentHashMap<>(256);

	private final List<Object> syncServices = new ArrayList<>();

	private final SyncConfigurationProperties syncConfigurationProperties;
	private final LockRegistry lockRegistry;
	private final MeterRegistry meterRegistry;
	private final ApplicationEventPublisher applicationEventPublisher;

	private volatile SyncServer serverStarter;

	private ConfigurableListableBeanFactory beanFactory;

	private StandardBeanExpressionResolver expressionResolver;

	private BeanExpressionContext expressionContext;


	private ScheduledExecutorService outboxScheduler;
	private ScheduledFuture<?> outboxJob;

	private boolean initialized = false;

	public SyncServicesManagement(SyncConfigurationProperties syncConfigurationProperties,
	                              LockRegistry lockRegistry,
	                              MeterRegistry meterRegistry,
	                              ApplicationEventPublisher applicationEventPublisher) {
		this.syncConfigurationProperties = syncConfigurationProperties;
		this.lockRegistry = lockRegistry;
		this.meterRegistry = meterRegistry;
		this.applicationEventPublisher = applicationEventPublisher;
	}

	@Override
	public int getOrder() {
		return Ordered.LOWEST_PRECEDENCE;
	}

	@Override
	public void setBeanFactory(@NonNull BeanFactory beanFactory) throws BeansException {
		if (beanFactory instanceof ConfigurableListableBeanFactory listableBeanFactory) {
			this.beanFactory = listableBeanFactory;
		}
	}

	@Override
	public void afterPropertiesSet() {
		Assert.state(syncConfigurationProperties != null, "The 'syncConfigurationProperties' property is required: " +
				"If there are no sync service logic, then don't use a sync aspect.");

		expressionResolver = new StandardBeanExpressionResolver(beanFactory.getBeanClassLoader());
		expressionContext = new BeanExpressionContext(beanFactory, null);
	}

	@Nullable
	public Object postProcessAfterInitialization(@NonNull Object bean, @NonNull String beanName) throws BeansException {
		final Class<?> beanClass = bean.getClass();
		if (beanClass.isAnnotationPresent(BindSyncService.class)) {
			syncServices.add(bean);
		}

		return bean;
	}

	@Override
	public void onApplicationEvent(@NonNull ApplicationEvent event) {
		if (event instanceof ApplicationStartedEvent) {
			startBindServices();
			startServiceContext();

			initialized = true;

			outboxScheduler = new ScheduledThreadPoolExecutor(
					1,
					(r, executor) -> {
						if (executor.isShutdown()) {
							return;
						}

						r.run();
					}
			);

			outboxJob = outboxScheduler.schedule(new OutboxRunner(), 10, TimeUnit.SECONDS);

			applicationEventPublisher.publishEvent(new SyncServicesStarted());
		} else if (event instanceof AbstractSyncServiceInitEvent e) {
			onServiceConnect(e);
		}
	}

	private void startServiceContext() {
		for (SyncServiceContext serviceContext : serviceContexts.values()) {
			final SyncConfigurationProperties.InitializeMode initializeMode = serviceContext.getProperties().getInitializeMode();
			if (initializeMode == SyncConfigurationProperties.InitializeMode.ON_STARTUP) {
				final String service = serviceContext.getProperties().getService();

				final boolean enabled = serviceContext.getProperties().isEnabled();
				if (!enabled) {
					log.info("Remote service [{}] disabled manually.", service);
					continue;
				}

				final String host = serviceContext.getProperties().getHost();
				final int port = serviceContext.getProperties().getPort();

				log.info("Try load remote service [{}] to {}:{}", service, host, port);
				serviceContext.createChannels();
			}
		}
	}

	private void onServiceConnect(AbstractSyncServiceInitEvent event) {
		final ConnectedServiceProperties source = event.getSource();

		final Class<?> svcClass = source.svcClass();
		final String serviceName = source.syncServiceName();
		final String syncHost = source.syncHost();
		final int syncPort = source.syncPort();

		manualCreateSyncServiceContext(svcClass, serviceName, syncHost, syncPort);
	}

	private void manualCreateSyncServiceContext(Class<?> svcClass, String serviceName, String host, int port) {
		serviceContexts
				.computeIfAbsent(
						serviceName,
						v -> {
							final Outbound outboundProperties = syncConfigurationProperties.findOutboundService(serviceName);
							if (outboundProperties == null) {
								throw new IllegalStateException("Outbound service configuration not found for [" + serviceName + "]. Please check it.");
							}

							final boolean enabled = outboundProperties.isEnabled();
							if (!enabled) {
								log.info("Remote service [{}] disabled manually.", outboundProperties.getService());
								return null;
							}

							final Transport transport = syncConfigurationProperties.getTransport();
							try {
								final SyncServiceContext context = new SyncServiceContext(transport, outboundProperties, svcClass);
								context.host = host;
								context.port = port;

								context.createChannels();

								return context;
							} catch (Exception e) {
								throw new RuntimeException(e);
							}
						});
	}

	private void startBindServices() {
		for (Object service : syncServices) {
			final BindSyncService bindSyncService = service.getClass().getDeclaredAnnotation(BindSyncService.class);
			final Class<?> svcClass = bindSyncService.service();
			final String condition = bindSyncService.condition();
			if (StringUtils.isNotEmpty(condition)) {
				final Boolean evaluate = (Boolean) expressionResolver.evaluate(condition, expressionContext);
				if (evaluate != null && evaluate) {
					bind(svcClass, service);
				}
			} else {
				bind(svcClass, service);
			}
		}
	}

	private void bind(Class<?> svcClass, Object remoteService) {
		if (remoteService.getClass().isAssignableFrom(svcClass)) {
			throw new RuntimeException(remoteService.getClass().getName() + " is not the implementation of " + svcClass.getName());
		}

		final String host = syncConfigurationProperties.getInbound().getHost();
		final int port = syncConfigurationProperties.getInbound().getPort();

		if (serverStarter == null) {
			synchronized (this) {
				if (serverStarter == null) {
					final String login = syncConfigurationProperties.getInbound().getLogin();
					final String password = syncConfigurationProperties.getInbound().getPassword();

					final Transport transport = syncConfigurationProperties.getTransport();

					serverStarter = new SyncServer(transport, login, password, host, port, lockRegistry, meterRegistry);
					serverStarter.start();
				}
			}
		}

		log.info("Bind remote service [{}] on {}:{}", svcClass.getSimpleName(), host, port);
		SyncServiceProvider.getInstance().bind(svcClass, remoteService);
	}

	public void tryInitConnectableSyncService(String serviceName, Class<?> serviceClass) {
		getContext(serviceClass, serviceName);
	}

	protected Object execute(Object target, Class<?> svcClass, Method method, Object[] args) throws Throwable {
		if (initialized) {
			final TargetClassMethodMetadata methodMetadata = getMethodMetadata(svcClass, method);

			String serviceName;
			if (methodMetadata.isHasServiceId()) {
				serviceName = (String) args[methodMetadata.getParametersIdx()];
			} else {
				serviceName = methodMetadata.getServiceName();
				if (StringUtils.isEmpty(serviceName)) {
					throw new IllegalStateException("Service name must be set on [" + svcClass.getName() + "]");
				}
			}

			final SyncServiceContext context = getContext(svcClass, serviceName);
			if (context == null) {
				throw new IllegalStateException("Outbound service configuration not found for [" + serviceName + "]. Please check it.");
			}

			final String name = method.getName();
			if (name.equalsIgnoreCase("equals")) {
				return context.handleEquals.invokeWithArguments(target, args[0]);
			}

			if (name.equalsIgnoreCase("hashCode")) {
				return context.handleHashCode.invoke(target);
			}

			if (name.equalsIgnoreCase("toString")) {
				return context.handleToString.invoke(target);
			}

			final Timer.Sample timer = startTimerIfPresent();

			final Type returnType = method.getGenericReturnType();
			Type typeArgument;
			if (returnType instanceof ParameterizedType) {
				typeArgument = ((ParameterizedType) returnType).getActualTypeArguments()[0];
			} else {
				typeArgument = returnType;
			}

			if (typeArgument == ClusterSyncLock.class) {
				return context.getOrCreateLock(meterRegistry, methodMetadata.isHasServiceId() ? (String) args[1] : (String) args[0]);
			}

			final ClientConnection connection = context.getConnection();

			final boolean noResultRequired = typeArgument == Void.class;
			final String invocationId = UUID.randomUUID().toString();
			final DefaultRequest defaultRequest = new DefaultRequest(invocationId, svcClass, method, args, noResultRequired);
			return connection
					.async(defaultRequest)
					.whenComplete((res, th) -> {
						if (timer != null) {
							timer.stop(methodMetadata.getTimer());
						}

						if (th != null) {
							final OutboxProcessorInformation outboxProcessorInformation = methodMetadata.getOutboxProcessorInformation();
							if (outboxProcessorInformation == null) {
								return;
							}

							if (outboxProcessorInformation.isNeedCatch(th)) {
								outboxProcessorInformation
										.processFail(invocationId, serviceName, svcClass, defaultRequest)
										.whenComplete((noOp, err) -> {
											if (err != null) {
												log.error("Outbox processor threw unexpected error. Request data might be lost.", err);
											}
										});
							}
						}
					});
		}

		return method.invoke(target, args);
	}

	private CompletableFuture<Object> executeByOutbox(String serviceName, Class<?> nestedServiceClass, DefaultRequest defaultRequest) {
		if (!initialized) {
			return CompletableFuture.failedFuture(new IllegalStateException("Service not initialized"));
		}

		final SyncServiceContext context = getContext(nestedServiceClass, serviceName);
		if (context == null) {
			throw new IllegalStateException("Outbound service configuration not found for [" + serviceName + "]. Please check it.");
		}

		final ClientConnection connection = context.getConnection();

		final String methodName = defaultRequest.getMethodName();
		final TargetClassMethodMetadata methodMetadata = getMethodMetadata(nestedServiceClass, methodName);
		if (methodMetadata != null) {
			final Timer.Sample timer = startTimerIfPresent();

			return connection
					.async(defaultRequest)
					.whenComplete((res, th) -> {
						if (timer != null) {
							timer.stop(methodMetadata.getTimer());
						}
					});
		}

		return connection.async(defaultRequest);
	}

	@Nullable
	private Timer.Sample startTimerIfPresent() {
		if (meterRegistry != null) {
			return Timer.start(meterRegistry);
		}

		return null;
	}

	private SyncServiceContext getContext(Class<?> svcClass, String serviceName) {
		if (!serviceContexts.containsKey(serviceName)) {
			final Outbound outboundProperties = syncConfigurationProperties.findOutboundService(serviceName);
			if (outboundProperties == null) {
				return null;
			}

			return serviceContexts
					.computeIfAbsent(
							serviceName,
							v -> {
								final Transport transport = syncConfigurationProperties.getTransport();

								try {
									return new SyncServiceContext(transport, outboundProperties, svcClass);
								} catch (Exception e) {
									throw new RuntimeException(e);
								}
							}
					);
		}

		return serviceContexts.get(serviceName);
	}

	private TargetClassMethodMetadata getMethodMetadata(Class<?> svcClass, Method method) {
		return methodsMetadata.computeIfAbsent(svcClass, v -> new ConcurrentHashMap<>())
				.computeIfAbsent(method.getName(), v -> new TargetClassMethodMetadata(beanFactory, meterRegistry, method, svcClass));
	}

	@Nullable
	private TargetClassMethodMetadata getMethodMetadata(Class<?> svcClass, String methodName) {
		return methodsMetadata.computeIfAbsent(svcClass, v -> new ConcurrentHashMap<>())
				.get(methodName);
	}

	@Override
	public void destroy() {
		TimeoutTimer.getTimer().cancel();

		if (outboxJob != null) {
			outboxJob.cancel(true);
			outboxJob = null;
		}

		for (SyncServiceContext context : serviceContexts.values()) {
			log.info("Close service {}", context.getProperties().getService());

			log.info("Shutdown service client...");
			final SyncClient client = context.client;
			if (client != null) {
				client.shutdownAsync();
			}

			log.info("Close service connections...");
			final ChannelSupplier<ClientConnection> channels = context.channels;
			if (channels != null) {
				for (ClientConnection connection : channels.getAll()) {
					connection.closeAsync();
				}
			}

			log.info("Destroy service shared locks...");
			context.locks.values().forEach(Destroyable::destroy);
		}
	}

	private class OutboxRunner implements Runnable {

		@Override
		@SuppressWarnings({"rawtypes", "unchecked"})
		public void run() {
			for (OutboxProcessor processor : outboxProcessors.values()) {
				final AtomicBoolean process = new AtomicBoolean(true);

				do {
					final Object txContext = processor.openTransaction();

					boolean error = false;

					try {
						final OutboxRecordInfo recordInfo = processor.next(txContext);
						if (recordInfo == null) {
							process.set(false);
							return;
						}

						try {
							executeByOutbox
									(
											recordInfo.serviceName(),
											recordInfo.nestedServiceClass(),
											recordInfo.defaultRequest()
									)
									.join();
							final String requestId = recordInfo.defaultRequest().getRequestId();
							processor.delete(txContext, requestId);
						} catch (Exception e) {
							log.error("Err while processing outbox.", e);
							process.set(false);
						}
					} catch (Exception e) {
						log.error("Err while processing outbox transaction.", e);
						error = true;
					} finally {
						processor.finishTransaction(txContext, error);
					}
				} while (process.get());
			}

			outboxJob = outboxScheduler.schedule(this, 10, TimeUnit.SECONDS);
		}
	}

	@Getter
	private static class TargetClassMethodMetadata {
		private String serviceName;
		private boolean hasServiceId;
		private int parametersIdx = -1;

		private Timer timer;

		private OutboxProcessorInformation outboxProcessorInformation;

		public TargetClassMethodMetadata(ConfigurableListableBeanFactory beanFactory, MeterRegistry meterRegistry, Method method, Class<?> svcClass) {
			for (int i = 0; i < method.getParameterCount(); i++) {
				final MethodParameter parameter = new MethodParameter(method, i);
				if (parameter.getParameterAnnotation(ServiceId.class) != null) {
					hasServiceId = true;
					parametersIdx = i;
					break;
				}
			}

			if (meterRegistry != null) {
				String timedOperationName, timerOperationDescription;
				if (method.isAnnotationPresent(TimedOperation.class)) {
					final TimedOperation timedOperation = method.getDeclaredAnnotation(TimedOperation.class);
					timedOperationName = StringUtils.isEmpty(timedOperation.operation())
							? "sync-client-operation#" + method.getName()
							: timedOperation.operation();
					timerOperationDescription = timedOperation.description();
				} else {
					timedOperationName = "sync-client-operation#" + method.getName();
					timerOperationDescription = StringUtils.EMPTY;
				}

				timer = Timer.builder(timedOperationName)
						.description(timerOperationDescription)
						.register(meterRegistry);
			}

			if (method.isAnnotationPresent(Outbox.class)) {
				final Outbox outbox = method.getDeclaredAnnotation(Outbox.class);
				final Class<? extends OutboxProcessor<?>> processorClass = outbox.processor();

				final OutboxProcessor<?> outboxProcessor = outboxProcessors.computeIfAbsent(
						processorClass,
						k -> beanFactory.getBean(processorClass)
				);

				outboxProcessorInformation = new OutboxProcessorInformation(
						outboxProcessor,
						outbox.catchOn()
				);
			}

			if (!hasServiceId) {
				final ConnectableSyncService remote = svcClass.getDeclaredAnnotation(ConnectableSyncService.class);
				if (remote != null) {
					serviceName = remote.service();
				}
			}
		}
	}

	private static class SyncServiceContext {
		private final Map<String, Lock> locks = new NonBlockingHashMap<>();

		private final Transport transport;
		@Getter
		private final Outbound properties;
		private final MethodHandle handleEquals, handleToString, handleHashCode;

		private String host;
		private int port;

		private SyncClient client;

		private ChannelSupplier<ClientConnection> channels;

		private final AtomicBoolean inititalized = new AtomicBoolean(false);

		private SyncServiceContext(Transport transport, Outbound properties, Class<?> svcClass)
				throws NoSuchMethodException, IllegalAccessException {
			this.transport = transport;
			this.properties = properties;

			final MethodHandles.Lookup lookup = MethodHandles.publicLookup();

			handleEquals = lookup
					.findVirtual(svcClass, "equals", MethodType.methodType(boolean.class, Object.class));
			handleToString = lookup
					.findVirtual(svcClass, "toString", MethodType.methodType(String.class));
			handleHashCode = lookup
					.findVirtual(svcClass, "hashCode", MethodType.methodType(int.class));


			host = properties.getHost();
			port = properties.getPort();
		}

		public void createChannels() {
			if (inititalized.compareAndSet(false, true)) {
				final long timeout = properties.getTimeout();

				final EventLoopGroup eventLoopGroup = LoopGroupFactory.getClientGroup(transport);

				if (timeout > 0) {
					client = SyncClient.create(host, port, eventLoopGroup, SyncServiceOptions.defaults().expectResultWithin(timeout));
				} else if (timeout == -1) {
					client = SyncClient.create(host, port, eventLoopGroup, SyncServiceOptions.defaults().noResult());
				} else {
					client = SyncClient.create(host, port, eventLoopGroup, SyncServiceOptions.defaults());
				}

				final String login = properties.getLogin();
				final String password = properties.getPassword();
				client.setLogin(login);
				client.setPassword(password);

				final int workers = properties.getConnections();

				if (workers > 0) {
					final ClientConnection[] connections = new ClientConnection[workers];
					for (int i = 0; i < workers; i++) {
						final ClientConnection clientConnection = client.connect();
						connections[i] = clientConnection;
					}

					channels = new ChannelSupplier<>(connections);
				} else {
					final ClientConnection clientConnection = client.connect();
					channels = new ChannelSupplier<>(new ClientConnection[]{clientConnection});
				}
			}
		}

		private void checkInitOrCreate() {
			if (!inititalized.get()) {
				if (StringUtils.isEmpty(host) && port == 0) {
					throw new IllegalStateException("Service [" + properties.getService() + "] " +
							"not initialized. Wait fully context initialization or manually create it.");
				}

				createChannels();
			}
		}

		public ClusterSyncLock getOrCreateLock(MeterRegistry meterRegistry, String entry) {
			checkInitOrCreate();

			return (ClusterSyncLock) locks.computeIfAbsent(entry, v -> {
				final long lockLeaseTimeout = properties.getLockLeaseTimeout();
				return new ClusterSyncLock(meterRegistry, entry, channels, lockLeaseTimeout);
			});
		}

		public ClientConnection getConnection() {
			checkInitOrCreate();
			return channels.get();
		}
	}
}
