package org.jd.net.core;

import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

@ChannelHandler.Sharable
public class CloseOnIOException extends ChannelInboundHandlerAdapter {
    static Logger logger = LoggerFactory.getLogger(CloseOnIOException.class);
    public static CloseOnIOException handler = new CloseOnIOException();

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        ctx.channel().close();
        if (!(cause instanceof IOException))
            logger.error("", cause);
    }
}
