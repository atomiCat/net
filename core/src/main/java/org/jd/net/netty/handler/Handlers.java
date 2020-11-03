package org.jd.net.netty.handler;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandler;
import io.netty.channel.ChannelInboundHandlerAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * Netty Handler工具类
 */
public interface Handlers {
    Logger logger = LoggerFactory.getLogger(Handlers.class);
    /**
     * 发生io异常时关闭当前Channel
     * 单例，可共享
     */
    ChannelInboundHandler closeOnIOException = new ChannelInboundHandlerAdapter() {
        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            if (cause instanceof IOException) {
                logger.error("closeOnIOException", cause);
                ctx.channel().close();
                return;
            }
            logger.error("", cause);
            ctx.fireExceptionCaught(cause);
        }

        @Override
        public boolean isSharable() {
            return true;
        }
    };

    /**
     * channelActive Handler
     */
    static ChannelInboundHandler active(Consumer<ChannelHandlerContext> channelActive) {
        return new ChannelInboundHandlerAdapter() {
            @Override
            public void channelActive(ChannelHandlerContext ctx) {
                channelActive.accept(ctx);
            }
        };
    }

    /**
     * channelRead Handler
     */
    static ChannelInboundHandler read(BiConsumer<ChannelHandlerContext, ByteBuf> channelRead) {
        return new ChannelInboundHandlerAdapter() {
            @Override
            public void channelRead(ChannelHandlerContext ctx, Object msg) {
                channelRead.accept(ctx, (ByteBuf) msg);
            }
        };
    }


}
