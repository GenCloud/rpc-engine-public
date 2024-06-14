package org.genfork.rpc.pipeline;

import io.micrometer.core.instrument.MeterRegistry;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import org.genfork.rpc.codec.*;
import org.genfork.rpc.handlers.AuthRequestHandler;
import org.genfork.rpc.handlers.DefaultRequestHandler;
import org.genfork.rpc.handlers.LockRequestHandler;
import org.genfork.rpc.lock.LockRegistry;

import java.nio.ByteOrder;

/**
 * @author: GenCloud
 * @date: 2023/01
 */
public class ServerChannelInitializer extends ChannelInitializer<SocketChannel> {
	private final LockRegistry lockRegistry;
	private final MeterRegistry meterRegistry;
	private final String login;
	private final String password;

	public ServerChannelInitializer(LockRegistry lockRegistry, MeterRegistry meterRegistry, String login, String password) {
		this.lockRegistry = lockRegistry;
		this.meterRegistry = meterRegistry;
		this.login = login;
		this.password = password;
	}

	@Override
	protected void initChannel(SocketChannel ch) {
		final ChannelPipeline pipeline = ch.pipeline();

		pipeline.addLast(
				new LengthFieldBasedFrameDecoder(
						ByteOrder.LITTLE_ENDIAN,
						32768 - 2, 0, 2, -2, 2,
						false
				),
				new RequestDecoder(),
				new LockRequestHandler(login, password, lockRegistry, meterRegistry),
				new AuthRequestHandler(login, password),
				new DefaultRequestHandler(login, password, meterRegistry)
		);

		pipeline.addLast(LengthEncoder.INSTANCE,
				LockReplyEncoder.INSTANCE, AuthReplyEncoder.INSTANCE, DefaultReplyEncoder.INSTANCE);
	}
}
