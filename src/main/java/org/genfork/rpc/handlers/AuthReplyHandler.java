package org.genfork.rpc.handlers;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import lombok.extern.slf4j.Slf4j;
import org.genfork.rpc.data.AuthReply;
import org.genfork.rpc.data.AuthRequestData;
import org.genfork.rpc.data.IRequest;
import org.genfork.rpc.data.RequestHolder;
import org.genfork.rpc.exceptions.AuthenticationException;

import java.util.Map;

/**
 * @author: GenCloud
 * @date: 2023/02
 */
@Slf4j
public class AuthReplyHandler extends SimpleChannelInboundHandler<AuthReply> {
	private RequestHolder getMessage(String requestId, ChannelHandlerContext ctx) {
		final Map<String, RequestHolder> map = ctx.channel().attr(MessageQueueHandler.REQUEST_MAP).get();
		return map.remove(requestId);
	}

	@Override
	protected void channelRead0(ChannelHandlerContext ctx, AuthReply authReply) throws Exception {
		final RequestHolder holder = getMessage(authReply.getRequestId(), ctx);
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
		final AuthRequestData<AuthReply> requestData = (AuthRequestData<AuthReply>) requestMessage;
		if (authReply.isSuccess()) {
			requestData.getPromise().complete(authReply);
		} else {
			final AuthenticationException exception = new AuthenticationException(authReply.getReason());
			log.error("", exception);
			requestData.tryFailure(exception);
		}
	}
}
