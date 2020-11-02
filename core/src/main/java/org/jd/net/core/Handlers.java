package org.jd.net.core;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;

import java.util.function.BiConsumer;
import java.util.function.Consumer;

public interface Handlers {
    CloseOnIOException closeOnIOException = new CloseOnIOException();

    static ChannelInboundHandlerAdapter active(Consumer<ChannelHandlerContext> channelActive) {
        return new ChannelInboundHandlerAdapter() {
            @Override
            public void channelActive(ChannelHandlerContext ctx) {
                channelActive.accept(ctx);
            }
        };
    }

    static ChannelInboundHandlerAdapter read(BiConsumer<ChannelHandlerContext, ByteBuf> channelRead) {
        return new ChannelInboundHandlerAdapter() {
            @Override
            public void channelRead(ChannelHandlerContext ctx, Object msg) {
                channelRead.accept(ctx, (ByteBuf) msg);
            }
        };
    }

}
