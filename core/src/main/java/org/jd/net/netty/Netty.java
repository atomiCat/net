package org.jd.net.netty;

import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioDatagramChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.Consumer;

public class Netty {
    private static Logger logger = LoggerFactory.getLogger(Netty.class);

    public static void setDefaultUncaughtExceptionHandler() {
        Thread.setDefaultUncaughtExceptionHandler((t, e) -> printError(e, "thread :" + t.getName() + " cause by "));
    }

    private static void printError(Throwable t, String tag) {
        for (; t != null; t = t.getCause()) {
            logger.error(tag + ":{} :{} ", t.getClass().getName(), t.getMessage());
            t.printStackTrace();
            for (Throwable sup : t.getSuppressed()) {
                printError(sup, tag + " suppressed ");
            }
        }
    }

    /**
     * connect
     *
     * @param host
     * @param port
     * @param channelHandler
     * @return
     */
    public static ChannelFuture connect(String host, int port, ChannelHandler channelHandler) {
        logger.info("connect {}:{}", host, port);
        EventLoopGroup workerGroup = new NioEventLoopGroup(1);
        Bootstrap b = new Bootstrap();
        b.group(workerGroup);
        b.channel(NioSocketChannel.class);
        b.handler(channelHandler);
        ChannelFuture connectFuture = b.connect(host, port);
        connectFuture.addListener(future -> {
            Channel channel = connectFuture.channel();
            if (future.isSuccess()) {
                channel.closeFuture().addListener(closeFuture -> {
                    logger.info("Connector shutdownGracefully because closed {} --> {}", channel.localAddress(), channel.remoteAddress());
                    workerGroup.shutdownGracefully();
                });
            } else {
                logger.info("Connector shutdownGracefully because connect fail {}:{}", host, port);
                workerGroup.shutdownGracefully();
            }
        });
        return connectFuture;
    }

    /**
     * connect
     *
     * @param initializer 初始化回调
     * @return
     */
    public static ChannelFuture connect(String host, int port, Consumer<Channel> initializer) {
        return connect(host, port, new ChannelInboundHandlerAdapter() {
            @Override
            public void channelRegistered(ChannelHandlerContext ctx) throws Exception {
                try {
                    initializer.accept(ctx.channel());
                    super.channelRegistered(ctx);
                } finally {//初始化结束，移除自己
                    ctx.pipeline().remove(this);
                }
            }

            @Override
            public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
                logger.error("connect fail", cause);
                ctx.channel().close();
                super.exceptionCaught(ctx, cause);
            }
        });
    }

    /**
     * accept
     *
     * @param port
     * @param childHandler
     * @return
     */
    public static ChannelFuture accept(String host, int port, ChannelHandler childHandler) {

        EventLoopGroup bossGroup = new NioEventLoopGroup(2);
        EventLoopGroup workerGroup = new NioEventLoopGroup(4);
        ServerBootstrap b = new ServerBootstrap();
        b.group(bossGroup, workerGroup)
                .channel(NioServerSocketChannel.class)
                .childHandler(childHandler);

        ChannelFuture bindFuture = b.bind(host, port);
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

    /**
     * @param port
     * @param childInitializer
     * @return
     */
    public static ChannelFuture accept(String host, int port, Consumer<Channel> childInitializer) {
        return accept(host, port, new ChannelInitializer<Channel>() {
            @Override
            protected void initChannel(Channel ch) {
                childInitializer.accept(ch);
            }
        });
    }

    public static ChannelFuture accept(int port, Consumer<Channel> childInitializer) {
        return accept("0.0.0.0", port, new ChannelInitializer<Channel>() {
            @Override
            protected void initChannel(Channel ch) {
                childInitializer.accept(ch);
            }
        });
    }

    public static ChannelFuture udp(int port, ChannelHandler handler) {
        EventLoopGroup group = new NioEventLoopGroup();
        Bootstrap b = new Bootstrap();
        b.group(group).channel(NioDatagramChannel.class)
                .handler(handler);
        ChannelFuture bindFuture = b.bind(port);
        bindFuture.addListener(future -> {
            if (future.isSuccess()) {
                bindFuture.channel().closeFuture().addListener(close -> {
                    logger.info("udp shutdownGracefully because closed port:{}", port);
                    group.shutdownGracefully();
                });
            } else {//绑定失败
                logger.info("udp shutdownGracefully because bind fail port:{}", port);
                group.shutdownGracefully();
            }
        });
        return bindFuture;
    }
}
