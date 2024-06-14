package org.genfork.rpc.handlers;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import lombok.extern.slf4j.Slf4j;
import org.genfork.rpc.data.DefaultReply;
import org.genfork.rpc.data.DefaultRequestData;
import org.genfork.rpc.data.IRequest;
import org.genfork.rpc.data.RequestHolder;

import java.util.Map;

/**
 * @author: GenCloud
 * @date: 2023/01
 */
@Slf4j
public class DefaultReplyHandler extends SimpleChannelInboundHandler<DefaultReply> {
	private RequestHolder getMessage(String requestId, ChannelHandlerContext ctx) {
		final Map<String, RequestHolder> map = ctx.channel().attr(MessageQueueHandler.REQUEST_MAP).get();
		return map.remove(requestId);
	}

	@Override
	protected void channelRead0(ChannelHandlerContext ctx, DefaultReply defaultReply) {
		final RequestHolder holder = getMessage(defaultReply.getRequestId(), ctx);
		final IRequest requestMessage = holder != null ? holder.getRequestMessage() : null;

		if (requestMessage == null) {
			return;
		}

		if (holder.getChannelPromise().isDone()
				&& !holder.getChannelPromise().isSuccess()) {
			return;
		}

		if (requestMessage.isExecuted()) {
			return;
		}

		@SuppressWarnings("unchecked")
		final DefaultRequestData<Object> defaultRequestData = (DefaultRequestData<Object>) requestMessage;

		final Object result = defaultReply.getResult();
		if (result instanceof Exception ex) {
			defaultRequestData.getPromise().completeExceptionally(ex);
		} else {
			defaultRequestData.getPromise().completeAsync(() -> result);
		}
	}
}
