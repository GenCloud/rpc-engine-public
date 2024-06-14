package org.genfork.rpc.data;

/**
 * @author: GenCloud
 * @date: 2023/01
 */
public interface IRequest {
	String requestId();

	boolean tryFailure(Throwable cause);

	boolean isExecuted();
}
