package org.jd.net.core;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DuplexTransfer extends ChannelInboundHandlerAdapter {
    private static final Logger logger = LoggerFactory.getLogger(DuplexTransfer.class);
    private final String host;
    private final int port;

    public DuplexTransfer(String host, int port) {
        this.host = host;
        this.port = port;
    }

    @Override
    public void handlerAdded(ChannelHandlerContext ctx) throws Exception {
        logger.info("setAutoRead(false)");
        ctx.channel().config().setAutoRead(false);//暂停自动读取
        if (ctx.channel().isActive()) {
            connect(ctx);
        }
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        connect(ctx);
        ctx.fireChannelActive();
    }

    private boolean connected;

    private void connect(ChannelHandlerContext ctx) {
        if (connected)
            throw new IllegalStateException("重复 connect");
        connected = true;

        Netty.connect(host, port, new ChannelInboundHandlerAdapter() {
            @Override
            public void channelRegistered(ChannelHandlerContext connected) throws Exception {
                connected.pipeline().addLast(
                        new Transfer(ctx),
                        new ChannelInboundHandlerAdapter() {
                            @Override
                            public void channelActive(ChannelHandlerContext connected) throws Exception {
                                ctx.pipeline().addLast(new Transfer(connected));
                                ctx.channel().config().setAutoRead(true);
                                super.channelActive(connected);
                            }
                        }
                );
            }
        }).addListener(future -> {
            if (!future.isSuccess())
                ctx.close();//连接失败，关闭client
        }).syncUninterruptibly();
    }
}
