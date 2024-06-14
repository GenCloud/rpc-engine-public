package org.genfork.rpc.data;

import lombok.Getter;

import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

/**
 * @author: GenCloud
 * @date: 2023/01
 */
@Getter
public class AuthRequestData<R> implements IRequest {
	private final CompletableFuture<R> promise;
	private final AuthRequest authRequest;

	public AuthRequestData(CompletableFuture<R> promise, AuthRequest authRequest) {
		this.promise = promise;
		this.authRequest = authRequest;
	}

	@Override
	public String requestId() {
		return authRequest.getRequestId();
	}

	@Override
	public boolean tryFailure(Throwable cause) {
		return promise.completeExceptionally(cause);
	}

	@Override
	public boolean isExecuted() {
		return promise.isDone();
	}

	public Throwable cause() {
		try {
			promise.getNow(null);
			return null;
		} catch (CompletionException e) {
			return e.getCause();
		} catch (CancellationException e) {
			return e;
		}
	}

	public boolean isSuccess() {
		return promise.isDone() && !promise.isCompletedExceptionally();
	}

	@Override
	public String toString() {
		return "AuthRequestData{" +
				"messageRequest=" + authRequest +
				'}';
	}
}
