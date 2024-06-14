package org.genfork.rpc.codec;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;
import org.genfork.rpc.KryoCodec;
import org.genfork.rpc.data.AuthReply;

/**
 * @author: GenCloud
 * @date: 2023/01
 */
@ChannelHandler.Sharable
public class AuthReplyEncoder extends MessageToByteEncoder<AuthReply> {
	public static final AuthReplyEncoder INSTANCE = new AuthReplyEncoder();

	@Override
	protected void encode(ChannelHandlerContext ctx, AuthReply msg, ByteBuf out) {
		final byte[] in = KryoCodec.write(msg);

		final ByteBuf byteBuf = Unpooled.directBuffer(in.length + 1);
		byteBuf.writeByte(2);
		byteBuf.writeBytes(in);

		out.writeBytes(byteBuf, byteBuf.readerIndex(), byteBuf.readableBytes());
		byteBuf.release();
	}
}
