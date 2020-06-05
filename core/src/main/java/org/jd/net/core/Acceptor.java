package org.jd.net.core;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.Supplier;

public class Acceptor implements Runnable {
    private Logger logger = LoggerFactory.getLogger(getClass());
    private final int port;
    private final Supplier<ChannelHandler[]> handlers;

    public Acceptor(int port, Supplier<ChannelHandler[]> handlers) {
        this.port = port;
        this.handlers = handlers;
    }

    EventLoopGroup bossGroup = new NioEventLoopGroup(); // (1)
    EventLoopGroup workerGroup = new NioEventLoopGroup();

    public void run() {
        ServerBootstrap b = new ServerBootstrap(); // (2)
        b.group(bossGroup, workerGroup)
                .channel(NioServerSocketChannel.class) // (3)
                .childHandler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) {
                        logger.info("accepted {}", ch.remoteAddress());
                        ch.pipeline().addLast(handlers.get());
                    }
                });
        // 等待服务器  socket 关闭 。
        try {
            channel = b.bind(port).sync().channel();// (7)
            channel.closeFuture().sync();
        } catch (InterruptedException e) {
            logger.error("", e);
            if (channel != null)
                channel.close();
        } finally {
            workerGroup.shutdownGracefully();
            bossGroup.shutdownGracefully();
            logger.info("Acceptor shutdownGracefully port:", port);
        }
    }

    private Channel channel;

    public void close() throws InterruptedException {
        if (channel != null)
            channel.close().sync();
    }
}
