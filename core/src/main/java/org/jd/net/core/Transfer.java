package org.jd.net.core;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;

public class Transfer extends ChannelInboundHandlerAdapter {
    private final ChannelHandlerContext target;

    public Transfer(ChannelHandlerContext target) {
        this.target = target;
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        target.close();
        super.channelInactive(ctx);
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
//        ByteBuf buf = (ByteBuf) msg;
//        CharSequence charSequence = buf.getCharSequence(0, buf.readableBytes(), StandardCharsets.UTF_8);
//        System.out.println(target.channel().remoteAddress() + "====>\n" + charSequence);
        target.writeAndFlush(msg);
    }
}
