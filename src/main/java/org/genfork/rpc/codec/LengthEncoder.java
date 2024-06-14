package org.genfork.rpc.codec;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToMessageEncoder;

import java.util.List;

/**
 * @author: GenCloud
 * @date: 2023/01
 */
@ChannelHandler.Sharable
public class LengthEncoder extends MessageToMessageEncoder<ByteBuf> {
	public static final LengthEncoder INSTANCE = new LengthEncoder();

	@Override
	protected void encode(ChannelHandlerContext ctx, ByteBuf msg, List<Object> out) {
		final ByteBuf buf = ctx.alloc().buffer(2);
		final short length = (short) (msg.readableBytes() + 2);
		buf.writeShortLE(length);
		out.add(buf);
		out.add(msg.retain());
	}
}
