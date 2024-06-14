package org.genfork.rpc.context;

import io.netty.channel.EventLoopGroup;
import io.netty.channel.ServerChannel;
import io.netty.channel.epoll.EpollEventLoopGroup;
import io.netty.channel.epoll.EpollServerSocketChannel;
import io.netty.channel.kqueue.KQueueEventLoopGroup;
import io.netty.channel.kqueue.KQueueServerSocketChannel;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import lombok.experimental.UtilityClass;

import java.util.concurrent.ThreadFactory;

/**
 * @author: GenCloud
 * @date: 2023/02
 */
@UtilityClass
public class LoopGroupFactory {
	private static volatile EventLoopGroup clientLoopGroup;
	private static volatile EventLoopGroup serverLoopGroup;

	public EventLoopGroup getClientGroup(Transport transport) {
		if (clientLoopGroup == null) {
			synchronized (LoopGroupFactory.class) {
				if (clientLoopGroup == null) {
					final ThreadFactory factory = Thread.ofVirtual().name("rpc-client-", 0).factory();
					clientLoopGroup = switch (transport) {
						case NIO -> new NioEventLoopGroup(4, factory);
						case KQUEUE -> new KQueueEventLoopGroup(4, factory);
						case EPOLL -> new EpollEventLoopGroup(4, factory);
					};
				}
			}
		}

		return clientLoopGroup;
	}

	public EventLoopGroup getServerGroup(Transport transport) {
		if (serverLoopGroup == null) {
			synchronized (LoopGroupFactory.class) {
				if (serverLoopGroup == null) {
					final ThreadFactory factory = Thread.ofVirtual().name("rpc-server-", 0).factory();
					serverLoopGroup = switch (transport) {
						case NIO -> new NioEventLoopGroup(4, factory);
						case KQUEUE -> new KQueueEventLoopGroup(4, factory);
						case EPOLL -> new EpollEventLoopGroup(4, factory);
					};
				}
			}
		}

		return serverLoopGroup;
	}

	public Class<? extends ServerChannel> getServerChannelClass(Transport transport) {
		return switch (transport) {
			case NIO -> NioServerSocketChannel.class;
			case KQUEUE -> KQueueServerSocketChannel.class;
			case EPOLL -> EpollServerSocketChannel.class;
		};
	}
}
