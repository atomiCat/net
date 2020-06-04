package org.jd.net.core;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.Supplier;

public class Connector implements Runnable {
    private Logger logger = LoggerFactory.getLogger(getClass());
    public final String host;
    public final int port;
    private SocketChannel channel;
    private final Supplier<ChannelHandler[]> handlers;


    public Connector(String host, int port, Supplier<ChannelHandler[]> handlers) {
        this.host = host;
        this.port = port;
        this.handlers = handlers;
    }

    @Override
    public void run() {
        EventLoopGroup workerGroup = new NioEventLoopGroup();
        try {
            Bootstrap b = new Bootstrap(); // (1)
            b.group(workerGroup); // (2)
            b.channel(NioSocketChannel.class); // (3)
            b.handler(
                    new ChannelInitializer<SocketChannel>() { // (4)
                        @Override
                        public void initChannel(SocketChannel ch) {
                            channel = ch;
                            logger.info("connected {}:{}", host, port);
                            ch.pipeline().addLast(handlers.get());
                        }
                    }
            );
            logger.info("connect {}:{}", host, port);
            // 启动客户端，等待连接关闭
            b.connect(host, port).sync().channel().closeFuture().sync(); // (5)
        } catch (InterruptedException e) {
            logger.error("", e);
        } finally {
            logger.info("Connector closed {}:{}", host, port);
            workerGroup.shutdownGracefully();
        }
    }

    public SocketChannel channel() {
        return channel;
    }

    public void close() {
        if (channel != null)
            channel.close();
    }
}
