package org.jd.net.core;

import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

@ChannelHandler.Sharable
public class CloseOnException extends ChannelInboundHandlerAdapter {
    static Logger logger = LoggerFactory.getLogger(CloseOnException.class);
    public static CloseOnException handler = new CloseOnException();

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        ctx.close();
        if (!(cause instanceof IOException))
            logger.error("", cause);
    }
}
