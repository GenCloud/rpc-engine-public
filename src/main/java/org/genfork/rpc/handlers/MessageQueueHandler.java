package org.genfork.rpc.handlers;

import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.util.AttributeKey;
import org.cliffc.high_scale_lib.NonBlockingHashMap;
import org.genfork.rpc.SyncServiceOptions;
import org.genfork.rpc.data.AuthRequestData;
import org.genfork.rpc.data.DefaultRequestData;
import org.genfork.rpc.data.LockRequestData;
import org.genfork.rpc.data.RequestHolder;
import org.genfork.rpc.exceptions.WriteConnectionException;

import java.util.Iterator;
import java.util.Map;

/**
 * @author: GenCloud
 * @date: 2023/01
 */
public class MessageQueueHandler extends ChannelDuplexHandler {
	public static final AttributeKey<Map<String, RequestHolder>> REQUEST_MAP = AttributeKey.valueOf("REQUEST_MAP");

	private final SyncServiceOptions options;

	public MessageQueueHandler(SyncServiceOptions options) {
		this.options = options;
	}

	@Override
	public void channelRegistered(ChannelHandlerContext ctx) throws Exception {
		super.channelRegistered(ctx);
		ctx.channel().attr(REQUEST_MAP).setIfAbsent(new NonBlockingHashMap<>());
	}

	@Override
	public void channelInactive(ChannelHandlerContext ctx) throws Exception {
		final Map<String, RequestHolder> reqMap = ctx.channel().attr(REQUEST_MAP).get();
		final Iterator<RequestHolder> iterator = reqMap.values().iterator();
		while (iterator.hasNext()) {
			final RequestHolder holder = iterator.next();
			iterator.remove();

			holder.getChannelPromise().tryFailure(
					new WriteConnectionException("Channel has been closed! Can't send request: "
							+ holder.getRequestMessage() + " to channel: " + ctx.channel()));
		}

		super.channelInactive(ctx);
	}

	@Override
	public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
		if (msg instanceof DefaultRequestData<?> request) {
			final boolean voidType = request.getDefaultRequest().isVoidType();
			final boolean resultExpected = options.isResultExpected();

			final RequestHolder holder = new RequestHolder(promise, request);
			if (voidType || !resultExpected) {
				request.getDefaultRequest().setNoResult(true);
				ctx.writeAndFlush(request, holder.getChannelPromise());

				request.getPromise().complete(null);
			} else {
				final Map<String, RequestHolder> map = ctx.channel().attr(REQUEST_MAP).get();

				final String requestId = request.requestId();
				map.put(requestId, holder);

				try {
					ctx.writeAndFlush(request, holder.getChannelPromise());
				} catch (Exception e) {
					map.remove(requestId);
					throw e;
				}
			}
		} else if (msg instanceof LockRequestData<?> lockRequest) {
			final RequestHolder holder = new RequestHolder(promise, lockRequest);
			final Map<String, RequestHolder> map = ctx.channel().attr(REQUEST_MAP).get();

			final String requestId = lockRequest.requestId();
			map.put(requestId, holder);

			try {
				ctx.writeAndFlush(lockRequest, holder.getChannelPromise());
			} catch (Exception e) {
				map.remove(requestId);
				throw e;
			}
		} else if (msg instanceof AuthRequestData<?> authRequest) {
			final RequestHolder holder = new RequestHolder(promise, authRequest);
			final Map<String, RequestHolder> map = ctx.channel().attr(REQUEST_MAP).get();

			final String requestId = authRequest.requestId();
			map.put(requestId, holder);

			try {
				ctx.writeAndFlush(authRequest, holder.getChannelPromise());
			} catch (Exception e) {
				map.remove(requestId);
				throw e;
			}
		}
	}
}
