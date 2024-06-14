package org.genfork.rpc.handlers;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.group.ChannelGroup;
import lombok.extern.slf4j.Slf4j;
import org.genfork.rpc.ClientConnection;
import org.genfork.rpc.SyncClient;
import org.genfork.rpc.TimeoutTimer;

import java.net.InetSocketAddress;
import java.util.concurrent.RejectedExecutionException;

/**
 * @author: GenCloud
 * @date: 2023/01
 */
@ChannelHandler.Sharable
@Slf4j
public class ChannelWatchdogHandler extends ChannelInboundHandlerAdapter {
	private final Bootstrap bootstrap;
	private final ChannelGroup channels;
	private static final int BACKOFF_CAP = 12;

	public ChannelWatchdogHandler(Bootstrap bootstrap, ChannelGroup channels) {
		this.bootstrap = bootstrap;
		this.channels = channels;
	}

	@Override
	public void channelActive(ChannelHandlerContext ctx) {
		channels.add(ctx.channel());
		ctx.fireChannelActive();
	}

	@Override
	public void channelInactive(ChannelHandlerContext ctx) {
		final ClientConnection connection = ClientConnection.getFrom(ctx.channel());
		if (connection != null) {
			if (!connection.isClosed()) {
				if (connection.isFastReconnect()) {
					tryReconnect(connection, 1);
				} else {
					reconnect(connection, 1);
				}
			}
		}
		ctx.fireChannelInactive();
	}

	private void reconnect(ClientConnection connection, final int attempts) {
		int timeout = 2 << attempts;
		if (bootstrap.config().group().isShuttingDown()) {
			return;
		}

		try {
			TimeoutTimer.getTimer().submit(() -> tryReconnect(connection, Math.min(BACKOFF_CAP, attempts + 1)), timeout);
		} catch (IllegalStateException e) {
			// skip
		}
	}

	private void tryReconnect(ClientConnection connection, int nextAttempt) {
		final SyncClient syncClient = connection.getSyncClient();
		if (syncClient.isShutdown() || connection.isClosed() || bootstrap.config().group().isShuttingDown()) {
			return;
		}

		final InetSocketAddress resolvedAddr = syncClient.getResolvedAddr();
		log.debug("Reconnecting {} to {} ...", connection, resolvedAddr);

		try {
			bootstrap
					.connect(resolvedAddr)
					.addListener(
							(ChannelFutureListener) future -> {
								if (syncClient.isShutdown() || connection.isClosed()
										|| bootstrap.config().group().isShuttingDown()) {
									if (future.isSuccess()) {
										final Channel ch = future.channel();
										final ClientConnection con = ClientConnection.getFrom(ch);
										if (con != null) {
											con.closeAsync();
										}
									}
									return;
								}

								if (future.isSuccess()) {
									final Channel channel = future.channel();
									if (channel.localAddress().equals(channel.remoteAddress())) {
										channel.close();
										log.error("local address and remote address are the same! connected to: {}, localAddress: {} remoteAddress: {}",
												resolvedAddr, channel.localAddress(), channel.remoteAddress());
									} else {
										final ClientConnection c = ClientConnection.getFrom(channel);
										c.getConnectionPromise()
												.whenComplete((res, e) -> {
													if (e == null) {
														if (syncClient.isShutdown() || connection.isClosed()) {
															channel.close();
															return;
														} else {
															log.debug("{} connected to {}", connection, resolvedAddr);
														}

														refresh(connection, channel);
													} else {
														channel.close();
														reconnect(connection, nextAttempt);
													}
												});
										return;
									}
								}

								reconnect(connection, nextAttempt);
							});
		} catch (RejectedExecutionException e) {
			// skip
		}
	}

	private void refresh(ClientConnection connection, Channel channel) {
		connection.updateChannel(channel);

		if (connection.isFastReconnect()) {
			connection.clearFastReconnect();
		}
	}
}
