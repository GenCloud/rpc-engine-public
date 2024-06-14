package org.genfork.rpc.codec;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.handler.codec.MessageToByteEncoder;
import org.genfork.rpc.KryoCodec;
import org.genfork.rpc.data.DefaultRequest;
import org.genfork.rpc.data.DefaultRequestData;

/**
 * @author: GenCloud
 * @date: 2023/01
 */
@ChannelHandler.Sharable
public class DefaultRequestEncoder extends MessageToByteEncoder<DefaultRequestData<?>> {
	public static final DefaultRequestEncoder INSTANCE = new DefaultRequestEncoder();

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
	protected void encode(ChannelHandlerContext ctx, DefaultRequestData<?> msg, ByteBuf out) throws Exception {
		final DefaultRequest defaultRequest = msg.getDefaultRequest();
		final byte[] in = KryoCodec.write(defaultRequest);

		final ByteBuf byteBuf = Unpooled.directBuffer(in.length + 1);
		byteBuf.writeByte(0);
		byteBuf.writeBytes(in);

		out.writeBytes(byteBuf, byteBuf.readerIndex(), byteBuf.readableBytes());
		byteBuf.release();
	}
}
