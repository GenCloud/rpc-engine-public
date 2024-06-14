package org.genfork.rpc.handlers;

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.util.AttributeKey;
import org.apache.commons.lang3.StringUtils;
import org.genfork.rpc.data.AuthReply;
import org.genfork.rpc.data.AuthRequest;

/**
 * @author: GenCloud
 * @date: 2023/02
 */
public class AuthRequestHandler extends SimpleChannelInboundHandler<AuthRequest> {
	public static final AttributeKey<Boolean> AUTH_CHECK = AttributeKey.valueOf("AUTH_CHECK");
	private final String serverLogin, serverPassword;

	public AuthRequestHandler(String serverLogin, String serverPassword) {
		this.serverLogin= serverLogin;
		this.serverPassword = serverPassword;
	}

	@Override
	protected void channelRead0(ChannelHandlerContext ctx, AuthRequest authRequest) throws Exception {
		final String requestId = authRequest.getRequestId();

		final Channel channel = ctx.channel();

		if (StringUtils.isNotEmpty(serverLogin) && StringUtils.isNotEmpty(serverPassword)) {
			final String login = authRequest.getLogin();
			final String password = authRequest.getPassword();

			if (serverLogin.equals(login) && serverPassword.equals(password)) {
				channel.attr(AUTH_CHECK).set(true);

				channel.writeAndFlush(new AuthReply(requestId, true, null));
			} else {
				channel.attr(AUTH_CHECK).set(false);

				channel.writeAndFlush(new AuthReply(requestId, false, "Invalid credentials."));
			}
		} else {
			channel.attr(AUTH_CHECK).set(true);

			final AuthReply reply = new AuthReply(requestId, true, null);
			channel.writeAndFlush(reply);
		}
	}
}
