package org.genfork.rpc;

import io.micrometer.core.instrument.MeterRegistry;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import lombok.extern.slf4j.Slf4j;
import org.genfork.rpc.context.LoopGroupFactory;
import org.genfork.rpc.context.Transport;
import org.genfork.rpc.exceptions.BindException;
import org.genfork.rpc.lock.LockRegistry;
import org.genfork.rpc.pipeline.ServerChannelInitializer;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @author: GenCloud
 * @date: 2023/01
 */
@Slf4j
public class SyncServer {
	private final EventLoopGroup workerGroup, bossGroup;
	private final String login, password;
	private final String host;
	private final int port;
	private final LockRegistry lockRegistry;
	private final MeterRegistry meterRegistry;
	private final Class<? extends ServerChannel> channelClass;

	private ChannelFuture channelFuture;

	private final AtomicBoolean initialized = new AtomicBoolean(false);

	public SyncServer(Transport transport, String login, String password, String host, int port, LockRegistry lockRegistry, MeterRegistry meterRegistry) {
		this.login = login;
		this.password = password;
		this.host = host;
		this.port = port;
		this.lockRegistry = lockRegistry;
		this.meterRegistry = meterRegistry;

		channelClass = LoopGroupFactory.getServerChannelClass(transport);

		final EventLoopGroup loopGroup = LoopGroupFactory.getServerGroup(transport);
		workerGroup = loopGroup;
		bossGroup = loopGroup;
	}

	public void start() {
		if (!initialized.compareAndSet(false, true)) {
			return;
		}

		if (channelFuture != null && !channelFuture.isDone()) {
			return;
		}

		try {
			final ServerBootstrap serverBootstrap = new ServerBootstrap()
					.group(bossGroup, workerGroup)
					.channel(channelClass)
					.childHandler(new ServerChannelInitializer(lockRegistry, meterRegistry, login, password))
					.childOption(ChannelOption.AUTO_READ, true)
					.childOption(ChannelOption.SO_SNDBUF, 4096)
					.childOption(ChannelOption.SO_RCVBUF, 4096)
					.childOption(ChannelOption.TCP_NODELAY, true)
					.childOption(ChannelOption.SO_KEEPALIVE, true)
					.childOption(ChannelOption.ALLOCATOR, PooledByteBufAllocator.DEFAULT);

			channelFuture = serverBootstrap.bind(host, port).sync();
			log.info("Start sync server on {}:{}", host, port);
		} catch (Exception e) {
			throw new BindException("Fail initialize sync server.", e);
		}
	}

	public void stop() throws InterruptedException, ExecutionException, TimeoutException {
		channelFuture.channel().close().sync().get(5_000, TimeUnit.MILLISECONDS);
	}
}
