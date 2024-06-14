package org.genfork.rpc.handlers;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.genfork.rpc.SyncServiceProvider;
import org.genfork.rpc.data.AuthReply;
import org.genfork.rpc.data.DefaultReply;
import org.genfork.rpc.data.DefaultRequest;
import org.genfork.rpc.exceptions.BindException;
import org.genfork.rpc.exceptions.RemoteException;
import org.genfork.rpc.exceptions.RequestTimeoutException;
import org.genfork.rpc.exceptions.ServiceRemoteException;
import org.springframework.lang.Nullable;

import java.util.concurrent.CompletableFuture;

/**
 * @author: GenCloud
 * @date: 2023/01
 */
@Slf4j
public class DefaultRequestHandler extends SimpleChannelInboundHandler<DefaultRequest> {
	private final boolean checkCredentials;
	private final MeterRegistry meterRegistry;

	public DefaultRequestHandler(String login, String password, MeterRegistry meterRegistry) {
		this.meterRegistry = meterRegistry;

		checkCredentials = StringUtils.isNotEmpty(login) && StringUtils.isNotEmpty(password);
	}

	@Override
	protected void channelRead0(ChannelHandlerContext ctx, DefaultRequest defaultRequest) throws Exception {
		final String requestId = defaultRequest.getRequestId();

		final Channel channel = ctx.channel();
		final Boolean authed = channel.attr(AuthRequestHandler.AUTH_CHECK).get();
		if ((authed == null || !authed) && checkCredentials) {
			channel.writeAndFlush(new AuthReply(requestId, false, "Invalid credentials."));
			channel.close();
			return;
		}

		final boolean noResult = defaultRequest.isNoResult();

		Object result;
		try {
			final long deadline = defaultRequest.getDeadline();
			if (deadline != -1) {
				final long currentTime = System.currentTimeMillis();
				if (currentTime > deadline) {
					throw new RequestTimeoutException("Request " + requestId + " is expired. Skip.");
				}
			}

			final Class<?> svcClass = defaultRequest.getServiceClass();
			final Object service = SyncServiceProvider.getInstance().getService(svcClass);
			if (service == null) {
				result = new BindException("Service " + svcClass.getName() + " is not provided");
			} else {
				result = defaultRequest.invoke(meterRegistry, service);
			}
		} catch (Throwable e) {
			if (e instanceof RequestTimeoutException) {
				return;
			}

			if (e instanceof RemoteException) {
				result = e;
			} else {
				result = new ServiceRemoteException(e);
			}
		}

		if (noResult) {
			return;
		}

		if (result instanceof CompletableFuture<?> future) {
			future.whenComplete((res, th) -> {
				DefaultReply defaultReply;
				if (th != null) {
					defaultReply = new DefaultReply(requestId, th);
				} else {
					defaultReply = new DefaultReply(requestId, res);
				}

				channel.writeAndFlush(defaultReply, channel.voidPromise());
			});
		} else {
			final DefaultReply defaultReply = new DefaultReply(requestId, result);
			channel.writeAndFlush(defaultReply, channel.voidPromise());
		}
	}
}
