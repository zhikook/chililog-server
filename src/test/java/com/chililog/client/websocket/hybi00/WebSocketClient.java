
package com.chililog.client.websocket.hybi00;

import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.handler.codec.http.websocket.WebSocketFrame;

/**
 * Copied from https://github.com/cgbystrom/netty-tools
 * 
 * @author vibul
 * 
 */
public interface WebSocketClient
{
    /**
     * Connect to server Host and port is setup by the factory.
     * 
     * @return Connect future. Fires when connected.
     */
    public ChannelFuture connect();

    /**
     * Disconnect from the server
     * 
     * @return Disconnect future. Fires when disconnected.
     */
    public ChannelFuture disconnect();

    /**
     * Send data to server
     * 
     * @param frame
     *            Data for sending
     * @return Write future. Will fire when the data is sent.
     */
    public ChannelFuture send(WebSocketFrame frame);
}
