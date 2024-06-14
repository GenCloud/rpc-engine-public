package org.genfork.rpc.codec;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;
import lombok.extern.slf4j.Slf4j;
import org.genfork.rpc.KryoCodec;
import org.genfork.rpc.data.AuthReply;
import org.genfork.rpc.data.DefaultReply;
import org.genfork.rpc.data.LockReply;

import java.util.List;

/**
 * @author: GenCloud
 * @date: 2023/01
 */
@Slf4j
public class ReplyDecoder extends ByteToMessageDecoder {
	@Override
	protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {
		if (!in.isReadable()) {
			throw new UnsupportedOperationException();
		}

		final short requestType = in.readUnsignedByte();

		final byte[] array = new byte[in.readableBytes()];
		in.readBytes(array);

		if (requestType == 0) {
			final DefaultReply defaultReply = KryoCodec.read(array, DefaultReply.class);
			out.add(defaultReply);
		} else if (requestType == 1) {
			final LockReply lockReply = KryoCodec.read(array, LockReply.class);
			out.add(lockReply);
		} else if (requestType == 2) {
			final AuthReply authReply = KryoCodec.read(array, AuthReply.class);
			out.add(authReply);
		}
	}
}
