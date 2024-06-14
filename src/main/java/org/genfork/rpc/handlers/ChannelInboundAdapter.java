package org.genfork.rpc.handlers;

import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;

/**
 * @author: GenCloud
 * @date: 2023/01
 */
public abstract class ChannelInboundAdapter<T extends ChannelInboundAdapter<?>> extends SimpleChannelInboundHandler<ByteBuf> {
	protected Channel channel;

	@Override
	public void channelActive(ChannelHandlerContext ctx) {
		channel = ctx.channel();
	}
}
