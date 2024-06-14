package org.genfork.rpc.codec;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;
import lombok.extern.slf4j.Slf4j;
import org.genfork.rpc.KryoCodec;
import org.genfork.rpc.data.AuthRequest;
import org.genfork.rpc.data.DefaultRequest;
import org.genfork.rpc.data.LockRequest;

import java.util.List;

/**
 * @author: GenCloud
 * @date: 2023/01
 */
@Slf4j
public class RequestDecoder extends ByteToMessageDecoder {
	@Override
	protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {
		if (!in.isReadable()) {
			throw new UnsupportedOperationException();
		}

		final short requestType = in.readUnsignedByte();

		final byte[] array = new byte[in.readableBytes()];
		in.readBytes(array);

		if (requestType == 0) {
			final DefaultRequest defaultRequest = KryoCodec.read(array, DefaultRequest.class);
			out.add(defaultRequest);
		} else if (requestType == 1) {
			final LockRequest lockRequest = KryoCodec.read(array, LockRequest.class);
			out.add(lockRequest);
		} else if (requestType == 2) {
			final AuthRequest authRequest = KryoCodec.read(array, AuthRequest.class);
			out.add(authRequest);
		}
	}
}
