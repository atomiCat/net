package org.jd.net.core;

import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandler;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Netty {
    private static Logger logger = LoggerFactory.getLogger(Netty.class);

    public static ChannelFuture connect(String host, int port, ChannelHandler channelHandler) {
        logger.info("connect {}:{}", host, port);
        EventLoopGroup workerGroup = new NioEventLoopGroup(1);
        Bootstrap b = new Bootstrap(); // (1)
        b.group(workerGroup); // (2)
        b.channel(NioSocketChannel.class); // (3)
        b.handler(channelHandler);
        ChannelFuture connectFuture = b.connect(host, port);// (5)
        connectFuture.addListener(future -> {
            if (future.isSuccess()) {
                connectFuture.channel().closeFuture().addListener(closeFuture -> {
                    logger.info("Connector shutdownGracefully because closed {}:{}", host, port);
                    workerGroup.shutdownGracefully();
                });
            } else {
                logger.info("Connector shutdownGracefully because connect fail {}:{}", host, port);
                workerGroup.shutdownGracefully();
            }
        });
        return connectFuture;
    }

    public static ChannelFuture accept(int port, ChannelHandler childHandler) {

        EventLoopGroup bossGroup = new NioEventLoopGroup(1); // (1)
        EventLoopGroup workerGroup = new NioEventLoopGroup();
        ServerBootstrap b = new ServerBootstrap(); // (2)
        b.group(bossGroup, workerGroup)
                .channel(NioServerSocketChannel.class) // (3)
                .childHandler(childHandler);

        ChannelFuture bindFuture = b.bind(port);
        bindFuture.addListener(future -> {
            if (future.isSuccess()) {
                bindFuture.channel().closeFuture().addListener(close -> {
                    logger.info("Acceptor shutdownGracefully because closed port:", port);
                    workerGroup.shutdownGracefully();
                    bossGroup.shutdownGracefully();
                });
            } else {//绑定失败
                logger.info("Acceptor shutdownGracefully because bind fail port:", port);
                workerGroup.shutdownGracefully();
                bossGroup.shutdownGracefully();
            }
        });
        return bindFuture;
    }
}
