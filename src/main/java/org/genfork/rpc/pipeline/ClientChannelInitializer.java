package org.genfork.rpc.pipeline;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.group.ChannelGroup;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import org.genfork.rpc.SyncClient;
import org.genfork.rpc.codec.*;
import org.genfork.rpc.handlers.*;

import java.nio.ByteOrder;

/**
 * @author: GenCloud
 * @date: 2023/01
 */
public class ClientChannelInitializer extends ChannelInitializer<NioSocketChannel> {
	private final SyncClient syncClient;
	private final ChannelWatchdogHandler connectionWatchdog;

	public ClientChannelInitializer(SyncClient syncClient,
	                                Bootstrap bootstrap,
	                                ChannelGroup channels) {
		this.syncClient = syncClient;

		connectionWatchdog = new ChannelWatchdogHandler(bootstrap, channels);
	}

	@Override
	protected void initChannel(NioSocketChannel ch) {
		final ChannelPipeline pipeline = ch.pipeline();

		pipeline.addLast(new ConnectionHandler(syncClient));

		pipeline.addLast(connectionWatchdog, LengthEncoder.INSTANCE,
				LockRequestEncoder.INSTANCE, AuthRequestEncoder.INSTANCE, DefaultRequestEncoder.INSTANCE);

		pipeline.addLast(new MessageQueueHandler(syncClient.getOptions()));

		pipeline.addLast(
				new LengthFieldBasedFrameDecoder(
						ByteOrder.LITTLE_ENDIAN,
						32768 - 2, 0, 2, -2, 2,
						false
				),
				new ReplyDecoder(),
				new LockReplyHandler(),
				new AuthReplyHandler(),
				new DefaultReplyHandler()
		);
	}
}
