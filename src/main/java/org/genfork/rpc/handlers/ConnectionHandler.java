package org.genfork.rpc.handlers;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import org.apache.commons.lang3.StringUtils;
import org.genfork.rpc.ClientConnection;
import org.genfork.rpc.SyncClient;
import org.genfork.rpc.data.AuthReply;
import org.genfork.rpc.data.AuthRequest;

import javax.naming.AuthenticationException;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * @author: GenCloud
 * @date: 2023/01
 */
public class ConnectionHandler extends ChannelInboundHandlerAdapter {
	private final SyncClient syncClient;
	private final CompletableFuture<ClientConnection> connectionPromise = new CompletableFuture<>();
	private ClientConnection connection;

	public ConnectionHandler(SyncClient syncClient) {
		super();
		this.syncClient = syncClient;
	}

	@Override
	public void channelRegistered(ChannelHandlerContext ctx) throws Exception {
		if (connection == null) {
			connection = new ClientConnection(syncClient, ctx.channel(), connectionPromise);
		}

		super.channelRegistered(ctx);
	}

	@Override
	public void channelActive(ChannelHandlerContext ctx) {
		final String clientLogin = syncClient.getLogin();
		final String clientPassword = syncClient.getPassword();

		if (StringUtils.isEmpty(clientLogin) && StringUtils.isEmpty(clientPassword)) {
			ctx.fireChannelActive();
			connectionPromise.complete(connection);
		} else {
			connection
					.async(
							new AuthRequest(UUID.randomUUID().toString(), clientLogin, clientPassword)
					)
					.whenComplete((res, th) -> {
						if (th != null) {
							connection.closeAsync();
							connectionPromise.completeExceptionally(th);
							return;
						}

						final AuthReply reply = (AuthReply) res;
						if (!reply.isSuccess()) {
							connection.closeAsync();
							connectionPromise.completeExceptionally(new AuthenticationException(reply.getReason()));
						} else {
							ctx.fireChannelActive();
							connectionPromise.complete(connection);
						}
					});
		}
	}
}
