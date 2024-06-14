package org.genfork.rpc.codec;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.handler.codec.MessageToByteEncoder;
import org.genfork.rpc.KryoCodec;
import org.genfork.rpc.data.AuthRequest;
import org.genfork.rpc.data.AuthRequestData;

/**
 * @author: GenCloud
 * @date: 2023/01
 */
@ChannelHandler.Sharable
public class AuthRequestEncoder extends MessageToByteEncoder<AuthRequestData<?>> {
	public static final AuthRequestEncoder INSTANCE = new AuthRequestEncoder();

	@Override
	public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
		if (acceptOutboundMessage(msg)) {
			if (!promise.setUncancellable()) {
				return;
			}
		}

		try {
			super.write(ctx, msg, promise);
		} catch (Exception e) {
			promise.tryFailure(e);
			throw e;
		}
	}

	@Override
	protected void encode(ChannelHandlerContext ctx, AuthRequestData<?> msg, ByteBuf out) throws Exception {
		final AuthRequest authRequest = msg.getAuthRequest();
		final byte[] in = KryoCodec.write(authRequest);

		final ByteBuf byteBuf = Unpooled.directBuffer(in.length + 1);
		byteBuf.writeByte(2);
		byteBuf.writeBytes(in);

		out.writeBytes(byteBuf, byteBuf.readerIndex(), byteBuf.readableBytes());
		byteBuf.release();
	}
}
