package org.genfork.rpc;

import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.util.AttributeKey;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.genfork.rpc.data.*;
import org.genfork.rpc.exceptions.RemoteServiceTimeoutException;
import org.genfork.rpc.exceptions.ShutdownException;
import org.genfork.rpc.handlers.MessageQueueHandler;
import org.genfork.rpc.timer.Timers.WheelTimer.TimerPausable;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * @author: GenCloud
 * @date: 2023/01
 */
@Slf4j
public class ClientConnection {
	private static final AttributeKey<ClientConnection> CONNECTION = AttributeKey.valueOf("connection");

	@Getter
	private final SyncClient syncClient;

	private volatile CompletableFuture<Void> fastReconnect;
	@Getter
	private volatile boolean closed;

	volatile Channel channel;

	private final CompletableFuture<?> connectionPromise;

	public <C> ClientConnection(SyncClient syncClient,
	                            Channel channel,
	                            CompletableFuture<C> connectionPromise) {
		this.syncClient = syncClient;
		this.connectionPromise = connectionPromise;

		updateChannel(channel);

		log.debug("Connection created.");
	}

	@SuppressWarnings("unchecked")
	public <C extends ClientConnection> CompletableFuture<C> getConnectionPromise() {
		return (CompletableFuture<C>) connectionPromise;
	}

	@SuppressWarnings("unchecked")
	public static <C extends ClientConnection> C getFrom(Channel channel) {
		return (C) channel.attr(ClientConnection.CONNECTION).get();
	}

	public boolean isOpen() {
		return channel.isOpen();
	}

	public boolean isActive() {
		return channel.isActive();
	}

	public void updateChannel(Channel channel) {
		if (channel == null) {
			throw new NullPointerException();
		}

		this.channel = channel;

		channel.attr(CONNECTION).set(this);
	}

	public ChannelFuture send(IRequest request) {
		return channel.writeAndFlush(request);
	}

	public <R> CompletableFuture<R> async(RequestMessage message) {
		if (syncClient.getEventLoopGroup().isShuttingDown()) {
			final ShutdownException cause = new ShutdownException("Client is shutdown");
			return CompletableFuture.failedFuture(cause);
		}

		final CompletableFuture<R> promise = new CompletableFuture<>();

		final ChannelFuture writeFuture = switch (message) {
			case AuthRequest authRequest -> getAuthRequestFuture(promise, authRequest);
			case DefaultRequest defaultRequest -> getDefaultRequestFuture(promise, defaultRequest);
			case LockRequest lockRequest -> getLockRequestFuture(promise, lockRequest);
			default ->
					throw new UnsupportedOperationException("Unsupported message type - " + message.getClass().getName());
		};

		writeFuture.addListener((ChannelFutureListener) future -> {
			if (!future.isSuccess()) {
				promise.completeExceptionally(future.cause());
			}
		});

		return promise;
	}

	private <R> ChannelFuture getAuthRequestFuture(CompletableFuture<R> promise, AuthRequest authRequest) {
		ChannelFuture writeFuture;
		final Long executionTimeoutInMillis = syncClient.getOptions().getExecutionTimeoutInMillis();
		if (executionTimeoutInMillis != null && executionTimeoutInMillis > 0) {
			authRequest.setDeadline(System.currentTimeMillis() + executionTimeoutInMillis);

			final TimerPausable pausable = startRequestTimeoutTimer(
					promise,
					authRequest.getRequestId(),
					executionTimeoutInMillis
			);

			promise.whenComplete((res, e) ->
					pausable.cancel());
		}

		writeFuture = send(new AuthRequestData<>(promise, authRequest));
		return writeFuture;
	}

	private <R> ChannelFuture getDefaultRequestFuture(CompletableFuture<R> promise, DefaultRequest defaultRequest) {
		ChannelFuture writeFuture;
		final Long executionTimeoutInMillis = syncClient.getOptions().getExecutionTimeoutInMillis();
		if (executionTimeoutInMillis != null && executionTimeoutInMillis > 0) {
			defaultRequest.setDeadline(System.currentTimeMillis() + executionTimeoutInMillis);

			final TimerPausable pausable = startRequestTimeoutTimer(
					promise,
					defaultRequest.getRequestId(),
					executionTimeoutInMillis
			);

			promise.whenComplete((res, e) ->
					pausable.cancel());
		}

		writeFuture = send(new DefaultRequestData<>(promise, defaultRequest));
		return writeFuture;
	}

	private <R> ChannelFuture getLockRequestFuture(CompletableFuture<R> promise, LockRequest lockRequest) {
		ChannelFuture writeFuture;
		final Long executionTimeoutInMillis = syncClient.getOptions().getExecutionTimeoutInMillis();
		if (lockRequest.getWaitTime() > 0) {
			final TimerPausable pausable = startRequestTimeoutTimer(
					promise,
					lockRequest.getRequestId(),
					lockRequest.getWaitTime()
			);

			promise.whenComplete((res, e) ->
					pausable.cancel());
		} else if (executionTimeoutInMillis != null && executionTimeoutInMillis > 0) {
			final TimerPausable pausable = startRequestTimeoutTimer(
					promise,
					lockRequest.getRequestId(),
					executionTimeoutInMillis
			);

			promise.whenComplete((res, e) ->
					pausable.cancel());
		}

		writeFuture = send(new LockRequestData<>(promise, lockRequest));
		return writeFuture;
	}

	private <R> TimerPausable startRequestTimeoutTimer(CompletableFuture<R> promise, String requestId, long timeout) {
		return TimeoutTimer
				.getTimer()
				.submit(() -> {
							final RemoteServiceTimeoutException ex =
									new RemoteServiceTimeoutException(
											"Execution timeout for request: " + requestId + ", Client: " + syncClient
									);

							if (!promise.completeExceptionally(ex)) {
								return;
							}

							final Map<String, RequestHolder> map = channel.attr(MessageQueueHandler.REQUEST_MAP).get();
							map.remove(requestId);
						},
						timeout
				);
	}

	private void close() {
		if (!connectionPromise.isDone()) {
			channel.close();
		}
	}

	public ChannelFuture closeAsync() {
		closed = true;
		close();
		return channel.closeFuture();
	}

	public boolean isFastReconnect() {
		return fastReconnect != null;
	}

	public void clearFastReconnect() {
		fastReconnect.complete(null);
		fastReconnect = null;
	}
}
