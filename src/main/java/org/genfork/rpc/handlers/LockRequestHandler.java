package org.genfork.rpc.handlers;

import io.micrometer.core.instrument.MeterRegistry;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.genfork.rpc.data.*;
import org.genfork.rpc.exceptions.MonitorNotOwnerException;
import org.genfork.rpc.lock.LocalSyncLock;
import org.genfork.rpc.lock.LockRegistry;

import java.util.concurrent.CompletableFuture;

/**
 * @author: GenCloud
 * @date: 2023/02
 */
@Slf4j
public class LockRequestHandler extends SimpleChannelInboundHandler<LockRequest> {
	private final LockRegistry lockRegistry;
	private final MeterRegistry meterRegistry;
	private final boolean checkCredentials;

	public LockRequestHandler(String login, String password, LockRegistry lockRegistry, MeterRegistry meterRegistry) {
		this.lockRegistry = lockRegistry;
		this.meterRegistry = meterRegistry;

		checkCredentials = StringUtils.isNotEmpty(login) && StringUtils.isNotEmpty(password);
	}

	@Override
	protected void channelRead0(ChannelHandlerContext ctx, LockRequest lockRequest) throws Exception {
		final String requestId = lockRequest.getRequestId();

		final Channel channel = ctx.channel();
		final Boolean authed = channel.attr(AuthRequestHandler.AUTH_CHECK).get();
		if ((authed == null || !authed) && checkCredentials) {
			channel.writeAndFlush(new AuthReply(requestId, false, "Invalid credentials."));
			channel.close();
			return;
		}

		final String entry = lockRequest.getEntry();
		final LockRequestType lockRequestType = lockRequest.getLockRequestType();

		final long threadId = lockRequest.getThreadId();
		final long leaseTime = lockRequest.getLeaseTime();
		final long waitTime = lockRequest.getWaitTime();

		final LocalSyncLock lock = lockRegistry.getLock(entry);

		final CompletableFuture<LockReply> promise = new CompletableFuture<>();

		if (lockRequestType == LockRequestType.ACQUIRE) {
			if (waitTime > 0) {
				lock
						.tryLockAsync(threadId, waitTime, leaseTime)
						.whenComplete((res, th) -> {
							if (th != null) {
								promise
										.complete(
												new LockReply(
														requestId,
														threadId,
														entry,
														LockAcquireType.RR_WAIT_TIMEOUT
												)
										);
							} else {
								promise
										.complete(
												new LockReply(
														requestId,
														threadId,
														entry,
														res ? LockAcquireType.SUCCESS : LockAcquireType.ALREADY_LOCKED
												)
										);
							}
						});
			} else {
				lock
						.tryLockAsync(threadId, 0, leaseTime)
						.whenComplete((res, th) ->
								promise
										.complete(
												new LockReply(
														requestId,
														threadId,
														entry,
														res ? LockAcquireType.SUCCESS : LockAcquireType.ALREADY_LOCKED
												)
										));
			}
		} else if (lockRequestType == LockRequestType.CHECK) {
			lock
					.isHeldBy(threadId)
					.whenComplete((res, th) ->
							promise
									.complete(
											new LockReply(
													requestId,
													threadId,
													entry,
													res ? LockAcquireType.ALREADY_LOCKED : LockAcquireType.RELEASED
											)
									)
					);
		} else {
			lock
					.unlockAsync(threadId)
					.whenComplete((res, th) -> {
						if (th != null) {
							promise
									.complete(
											new LockReply(
													requestId,
													threadId,
													entry,
													th instanceof MonitorNotOwnerException ? LockAcquireType.NOT_OWNED : LockAcquireType.RELEASED
											)
									);
						} else {
							promise
									.complete(
											new LockReply(
													requestId,
													threadId,
													entry,
													LockAcquireType.SUCCESS
											)
									);
						}
					});
		}

		promise.whenComplete((reply, th) ->
				channel.writeAndFlush(reply, channel.voidPromise()));
	}
}
