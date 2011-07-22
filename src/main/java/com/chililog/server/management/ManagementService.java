//
// Copyright 2010 Cinch Logic Pty Ltd.
//
// http://www.chililog.com
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
//

package com.chililog.server.management;

import java.net.InetSocketAddress;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.jboss.netty.bootstrap.ServerBootstrap;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelFactory;
import org.jboss.netty.channel.group.ChannelGroup;
import org.jboss.netty.channel.group.ChannelGroupFuture;
import org.jboss.netty.channel.group.DefaultChannelGroup;
import org.jboss.netty.channel.socket.nio.NioServerSocketChannelFactory;
import org.jboss.netty.handler.execution.ExecutionHandler;
import org.jboss.netty.handler.execution.OrderedMemoryAwareThreadPoolExecutor;

import com.chililog.server.common.AppProperties;
import com.chililog.server.common.Log4JLogger;

/**
 * <p>
 * The UiServerManager controls the embedded Netty web server used to provide ChiliLog's management, analysis and
 * notification functions to users.
 * </p>
 * 
 * <pre class="example">
 * // Start web server
 * UiServerManager.getInstance().start();
 * 
 * // Stop web server
 * UiServerManager.getInstance().stop();
 * </pre>
 * 
 * <p>
 * The web server's request handling pipeline is setup by {@link HttpServerPipelineFactory}. <a
 * href="http://www.jboss.org/netty/community#nabble-td3823513">Three</a> Netty thread pools are used:
 * <ul>
 * <li>One for the channel bosses (the server channel acceptors). See NioServerSocketChannelFactory javadoc.</li>
 * <li>One for the accepted channels (called workers). See NioServerSocketChannelFactory javadoc.</li>
 * <li>One for task processing, after the request has been decoded and understood. See ExecutionHandler and
 * OrderedMemoryAwareThreadPoolExecutor javadoc.</li>
 * </ul>
 * </p>
 * <p>
 * Here's a description of how it all works from http://www.jboss.org/netty/community#nabble-td3434933.
 * </p>
 * 
 * <pre class="example">
 * For posterity, updated notes on Netty's concurrency architecture:
 * 
 * After calling ServerBootstrap.bind(), Netty starts a boss thread that just accepts new connections and registers them
 * with one of the workers from the worker pool in round-robin fashion (pool size defaults to CPU count). Each worker
 * runs its own select loop over just the set of keys that have been registered with it. Workers start lazily on demand
 * and run only so long as there are interested fd's/keys. All selected events are handled in the same thread and sent
 * up the pipeline attached to the channel (this association is established by the boss as soon as a new connection is
 * accepted).
 * 
 * All workers, and the boss, run via the executor thread pool; hence, the executor must support at least two
 * simultaneous threads.
 * 
 * A pipeline implements the intercepting filter pattern. A pipeline is a sequence of handlers. Whenever a packet is
 * read from the wire, it travels up the stream, stopping at each handler that can handle upstream events. Vice-versa
 * for writes. Between each filter, control flows back through the centralized pipeline, and a linked list of contexts
 * keeps track of where we are in the pipeline (one context object per handler).
 * </pre>
 * <p>
 * The pipeline uses {@link HttpRequestHandler} to route requests to services for processing. Routing is based on the
 * request URI. Example of servers are {@link EchoService} and {@link StaticFileService}.
 * </p>
 * 
 * @author vibul
 * 
 */
public class ManagementService
{
    private static Log4JLogger _logger = Log4JLogger.getLogger(ManagementService.class);
    private static final ChannelGroup _allChannels = new DefaultChannelGroup("WebServerManager");
    private ChannelFactory _channelFactory = null;

    /**
     * Returns the singleton instance for this class
     */
    public static ManagementService getInstance()
    {
        return SingletonHolder.INSTANCE;
    }

    /**
     * SingletonHolder is loaded on the first execution of Singleton.getInstance() or the first access to
     * SingletonHolder.INSTANCE, not before.
     * 
     * See http://en.wikipedia.org/wiki/Singleton_pattern
     */
    private static class SingletonHolder
    {
        public static final ManagementService INSTANCE = new ManagementService();
    }

    /**
     * <p>
     * Singleton constructor
     * </p>
     * <p>
     * If there is an exception, we log the error and exit because there's no point continuing without MQ client session
     * </p>
     * 
     * @throws Exception
     */
    private ManagementService()
    {
        return;
    }

    /**
     * Start the web server
     */
    public void start()
    {
        AppProperties appProperties = AppProperties.getInstance();

        if (_channelFactory != null)
        {
            _logger.info("Web Sever Already Started.");
            return;
        }

        _logger.info("Starting UI Web Sever on " + AppProperties.getInstance().getManagementIpAddress() + ":"
                + AppProperties.getInstance().getManagementIpPort() + "...");

        _channelFactory = new NioServerSocketChannelFactory(Executors.newCachedThreadPool(),
                Executors.newCachedThreadPool());

        // Configure the server.
        ServerBootstrap bootstrap = new ServerBootstrap(_channelFactory);

        // Set up the event pipeline factory.
        ExecutionHandler executionHandler = new ExecutionHandler(new OrderedMemoryAwareThreadPoolExecutor(
                appProperties.getManagementTaskThreadPoolSize(), appProperties.getManagementTaskThreadPoolMaxChannelMemorySize(),
                appProperties.getManagementTaskThreadPoolMaxThreadMemorySize(),
                appProperties.getManagementTaskThreadPoolKeepAliveSeconds(), TimeUnit.SECONDS));

        bootstrap.setPipelineFactory(new HttpServerPipelineFactory(executionHandler));

        // Bind and start to accept incoming connections.
        InetSocketAddress socket = new InetSocketAddress(AppProperties.getInstance().getManagementIpAddress(), AppProperties
                .getInstance().getManagementIpPort());
        Channel channel = bootstrap.bind(socket);

        _allChannels.add(channel);

        _logger.info("UI Web Sever Started.");
    }

    /**
     * Stop the web server
     */
    public void stop()
    {
        _logger.info("Stopping Web Sever ...");

        ChannelGroupFuture future = _allChannels.close();
        future.awaitUninterruptibly();

        _channelFactory.releaseExternalResources();
        _channelFactory = null;

        _logger.info("Web Sever Stopped.");
    }
}