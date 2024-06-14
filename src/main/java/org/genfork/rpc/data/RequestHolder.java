package org.genfork.rpc.data;

import io.netty.channel.ChannelPromise;
import lombok.Getter;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @author: GenCloud
 * @date: 2023/01
 */
@Getter
public class RequestHolder {
	private final AtomicBoolean sent = new AtomicBoolean();
	private final ChannelPromise channelPromise;
	private final IRequest requestMessage;

	public RequestHolder(ChannelPromise channelPromise, IRequest requestMessage) {
		this.channelPromise = channelPromise;
		this.requestMessage = requestMessage;
	}
}
