package org.genfork.rpc;

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.channel.*;
import io.netty.channel.epoll.EpollDatagramChannel;
import io.netty.channel.epoll.EpollEventLoopGroup;
import io.netty.channel.epoll.EpollSocketChannel;
import io.netty.channel.group.ChannelGroup;
import io.netty.channel.group.ChannelGroupFuture;
import io.netty.channel.group.DefaultChannelGroup;
import io.netty.channel.kqueue.KQueueDatagramChannel;
import io.netty.channel.kqueue.KQueueEventLoopGroup;
import io.netty.channel.kqueue.KQueueSocketChannel;
import io.netty.channel.socket.DatagramChannel;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioDatagramChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.resolver.AddressResolver;
import io.netty.resolver.dns.DnsAddressResolverGroup;
import io.netty.resolver.dns.DnsServerAddressStreamProviders;
import io.netty.util.NetUtil;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.FutureListener;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.genfork.rpc.exceptions.AuthenticationException;
import org.genfork.rpc.exceptions.RemoteConnectionException;
import org.genfork.rpc.exceptions.RemoteException;
import org.genfork.rpc.exceptions.RemoteServiceConnectionException;
import org.genfork.rpc.pipeline.ClientChannelInitializer;
import org.springframework.retry.policy.AlwaysRetryPolicy;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.retry.support.RetryTemplateBuilder;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * @author: GenCloud
 * @date: 2023/01
 */
@Slf4j
public class SyncClient {
	private final AtomicReference<CompletableFuture<InetSocketAddress>> resolvedAddrFuture = new AtomicReference<>();

	@Getter
	private InetSocketAddress resolvedAddr;
	private final Bootstrap bootstrap;
	private final ChannelGroup channels;
	@Getter
	private final EventLoopGroup eventLoopGroup;
	private final String host;
	private final int port;
	@Getter
	private final SyncServiceOptions options;

	@Getter
	@Setter
	private String login, password;

	private final AtomicBoolean shutdown = new AtomicBoolean();

	private final RetryTemplate retryTemplate = new RetryTemplateBuilder()
			.customPolicy(new AlwaysRetryPolicy())
			.exponentialBackoff(1000, 1.1, 15000, true)
			.build();

	public static SyncClient create(String host, int port, EventLoopGroup eventLoopGroup) {
		return new SyncClient(host, port, eventLoopGroup, SyncServiceOptions.defaults());
	}

	public static SyncClient create(String host, int port, EventLoopGroup eventLoopGroup, SyncServiceOptions options) {
		return new SyncClient(host, port, eventLoopGroup, options);
	}

	private SyncClient(String host, int port, EventLoopGroup eventLoopGroup, SyncServiceOptions options) {
		this.host = host;
		this.port = port;
		this.eventLoopGroup = eventLoopGroup;
		this.options = options;

		channels = new DefaultChannelGroup(eventLoopGroup.next());

		Class<? extends SocketChannel> channelClass;
		if (eventLoopGroup instanceof EpollEventLoopGroup) {
			channelClass = EpollSocketChannel.class;
		} else if (eventLoopGroup instanceof KQueueEventLoopGroup) {
			channelClass = KQueueSocketChannel.class;
		} else {
			channelClass = NioSocketChannel.class;
		}

		bootstrap = createBootstrap(eventLoopGroup, channelClass);
	}

	private Bootstrap createBootstrap(EventLoopGroup eventLoopGroup, Class<? extends SocketChannel> channelClass) {
		Class<? extends DatagramChannel> dnsChannelClass;
		if (eventLoopGroup instanceof EpollEventLoopGroup) {
			dnsChannelClass = EpollDatagramChannel.class;
		} else if (eventLoopGroup instanceof KQueueEventLoopGroup) {
			dnsChannelClass = KQueueDatagramChannel.class;
		} else {
			dnsChannelClass = NioDatagramChannel.class;
		}

		final Bootstrap bootstrap = new Bootstrap()
				.resolver(new DnsAddressResolverGroup(dnsChannelClass, DnsServerAddressStreamProviders.platformDefault()))
				.channel(channelClass)
				.group(eventLoopGroup);

		bootstrap.handler(new ClientChannelInitializer(this, bootstrap, channels));
		bootstrap
				.option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 10_000)
				.option(ChannelOption.AUTO_READ, true)
				.option(ChannelOption.SO_SNDBUF, 4096)
				.option(ChannelOption.SO_RCVBUF, 4096)
				.option(ChannelOption.TCP_NODELAY, true)
				.option(ChannelOption.SO_KEEPALIVE, true)
				.option(ChannelOption.ALLOCATOR, PooledByteBufAllocator.DEFAULT);
		return bootstrap;
	}

	public CompletableFuture<InetSocketAddress> resolveAddr() {
		if (resolvedAddrFuture.get() != null) {
			return resolvedAddrFuture.get();
		}

		final CompletableFuture<InetSocketAddress> promise = new CompletableFuture<>();
		if (!resolvedAddrFuture.compareAndSet(null, promise)) {
			return resolvedAddrFuture.get();
		}

		final byte[] addr = NetUtil.createByteArrayFromIpAddressString(host);
		if (addr != null) {
			try {
				resolvedAddr = new InetSocketAddress(InetAddress.getByAddress(host, addr), port);
			} catch (UnknownHostException e) {
				// skip
			}

			promise.complete(resolvedAddr);
			return promise;
		}

		final EventLoop eventLoop =
				bootstrap.config().group().next();

		@SuppressWarnings("unchecked") final AddressResolver<InetSocketAddress> resolver =
				(AddressResolver<InetSocketAddress>) bootstrap.config().resolver().getResolver(eventLoop);
		final Future<InetSocketAddress> resolveFuture = resolver.resolve(InetSocketAddress.createUnresolved(host, port));
		resolveFuture.addListener((FutureListener<InetSocketAddress>) future -> {
			if (!future.isSuccess()) {
				promise.completeExceptionally(new RemoteConnectionException(future.cause()));
				return;
			}

			final InetSocketAddress resolved = future.getNow();
			final byte[] resolvedAddr = resolved.getAddress().getAddress();
			this.resolvedAddr = new InetSocketAddress(InetAddress.getByAddress(host, resolvedAddr), resolved.getPort());
			promise.complete(this.resolvedAddr);
		});
		return promise;
	}

	public ClientConnection connect() {
		try {
			return retryTemplate.execute(retryContext -> {
				try {
					return connectAsync().toCompletableFuture().join();
				} catch (CompletionException e) {
					if (e.getCause() instanceof RemoteException) {
						if (e.getCause() instanceof AuthenticationException) {
							log.error("Unauthorized.", e.getCause());
							return null;
						}

						throw e.getCause();
					} else {
						throw new RemoteServiceConnectionException("Unable to connect to: " + host + ":" + port, e);
					}
				}
			});
		} catch (Throwable th) {
			throw new RemoteServiceConnectionException("Connection refused. no future information on: " + host + ":" + port, th);
		}
	}

	private CompletableFuture<ClientConnection> connectAsync() {
		final CompletableFuture<InetSocketAddress> addrFuture = resolveAddr();

		return addrFuture
				.thenCompose(address -> {
					final CompletableFuture<ClientConnection> resultConFuture = new CompletableFuture<>();

					final ChannelFuture channelFuture = bootstrap.connect(address);
					channelFuture
							.addListener((ChannelFutureListener) future -> {
								final EventLoopGroup loopGroup = bootstrap.config().group();

								final boolean shuttingDown = loopGroup.isShuttingDown();
								if (shuttingDown) {
									resultConFuture.completeExceptionally(new RemoteServiceConnectionException("Client is shutdown"));
									return;
								}

								if (future.isSuccess()) {
									final ClientConnection clientConnection = ClientConnection.getFrom(future.channel());
									clientConnection.getConnectionPromise()
											.whenComplete((conRes, throwable) ->
													loopGroup
															.execute(() -> {
																if (throwable == null) {
																	if (!resultConFuture.complete(clientConnection)) {
																		clientConnection.closeAsync();
																	}
																} else {
																	resultConFuture.completeExceptionally(throwable);
																	clientConnection.closeAsync();
																}
															}));
								} else {
									loopGroup
											.execute(() ->
													resultConFuture.completeExceptionally(future.cause()));
								}
							});
					return resultConFuture;
				});
	}

	public void shutdown() {
		shutdownAsync().toCompletableFuture().join();
	}

	public CompletableFuture<Void> shutdownAsync() {
		if (!shutdown.compareAndSet(false, true)) {
			return CompletableFuture.failedFuture(new IllegalStateException("Client already in shutdown state."));
		}

		final CompletableFuture<Void> result = new CompletableFuture<>();
		if (channels.isEmpty() || eventLoopGroup.isShuttingDown()) {
			shutdown(result);
			return result;
		}

		final ChannelGroupFuture channelsFuture = channels.newCloseFuture();
		channelsFuture.addListener((FutureListener<Void>) future -> {
			if (!future.isSuccess()) {
				result.completeExceptionally(future.cause());
				return;
			}

			shutdown(result);
		});

		for (Channel channel : channels) {
			final ClientConnection connection = ClientConnection.getFrom(channel);
			if (connection != null) {
				connection.closeAsync();
			}
		}

		return result;
	}

	private void shutdown(CompletableFuture<Void> result) {
		Thread.ofVirtual().start(() -> {
			try {
				TimeoutTimer.getTimer().cancel();

				bootstrap.config().resolver().close();
				bootstrap.config().group().shutdownGracefully();
			} catch (Exception e) {
				result.completeExceptionally(e);
				return;
			}

			result.complete(null);
		});
	}

	public boolean isShutdown() {
		return shutdown.get();
	}
}
