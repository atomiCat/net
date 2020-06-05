package org.jd.net.core;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;

public class DuplexTransfer extends ChannelInboundHandlerAdapter {
    private final String host;
    private final int port;

    public DuplexTransfer(String host, int port) {
        this.host = host;
        this.port = port;
    }

    @Override
    public void handlerAdded(ChannelHandlerContext ctx) throws Exception {
        ctx.channel().config().setAutoRead(false);//暂停自动读取
    }

    @Override
    public void channelActive(ChannelHandlerContext client) throws Exception {
        Netty.connect(host, port, new ChannelInboundHandlerAdapter() {
            @Override
            public void channelRegistered(ChannelHandlerContext ctx) throws Exception {
                ctx.pipeline().addLast(
                        new Transfer(client),
                        new ChannelInboundHandlerAdapter() {
                            @Override
                            public void channelActive(ChannelHandlerContext ctx) throws Exception {
                                client.pipeline().addLast(new Transfer(ctx));
                                client.channel().config().setAutoRead(true);
                                super.channelActive(ctx);
                            }
                        }
                );
            }
        }).addListener(future -> {
            if (!future.isSuccess())
                client.close();//连接失败，关闭client
        });
        client.fireChannelActive();
    }
}
