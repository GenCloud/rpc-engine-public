package org.genfork.rpc.handlers;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import lombok.extern.slf4j.Slf4j;
import org.genfork.rpc.data.*;
import org.genfork.rpc.exceptions.MonitorNotOwnerException;

import java.util.Map;

/**
 * @author: GenCloud
 * @date: 2023/01
 */
@Slf4j
public class LockReplyHandler extends SimpleChannelInboundHandler<LockReply> {
	private RequestHolder getMessage(String requestId, ChannelHandlerContext ctx) {
		final Map<String, RequestHolder> map = ctx.channel().attr(MessageQueueHandler.REQUEST_MAP).get();
		return map.remove(requestId);
	}

	@Override
	protected void channelRead0(ChannelHandlerContext ctx, LockReply messageReply) {
		final RequestHolder holder = getMessage(messageReply.getRequestId(), ctx);
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
		final LockRequestData<LockRes> requestData = (LockRequestData<LockRes>) requestMessage;
		final LockAcquireType acquireType = messageReply.getType();

		final LockRequestType requestType = requestData.getLockRequest().getLockRequestType();
		if (requestType == LockRequestType.ACQUIRE) {
			if (acquireType == LockAcquireType.SUCCESS) {
				requestData.getPromise().complete(new LockRes(true));
			} else if (acquireType == LockAcquireType.ALREADY_LOCKED) {
				requestData.getPromise().complete(new LockRes(false));
			} else {
				requestData.getPromise().completeExceptionally(new IllegalMonitorStateException("Wait time exceeded."));
			}
		} else if (requestType == LockRequestType.CHECK) {
			if (acquireType == LockAcquireType.RELEASED) {
				requestData.getPromise().complete(new LockRes(false));
			} else if (acquireType == LockAcquireType.ALREADY_LOCKED) {
				requestData.getPromise().complete(new LockRes(true));
			}
		} else {
			if (acquireType == LockAcquireType.SUCCESS) {
				requestData.getPromise().complete(new LockRes(true));
			} else if (acquireType == LockAcquireType.NOT_OWNED) {
				requestData.getPromise().completeExceptionally(new MonitorNotOwnerException("Current thread not owned this lock."));
			} else if (acquireType == LockAcquireType.RELEASED) {
				requestData.getPromise().completeExceptionally(new IllegalMonitorStateException("Lock already released."));
			}
		}
	}
}
