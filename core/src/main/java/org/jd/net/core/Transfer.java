package org.jd.net.core;

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;

public class Transfer extends ChannelInboundHandlerAdapter {
    private final Channel target;
    private final boolean closeOnInactive;

    public Transfer(Channel target) {
        this.target = target;
        closeOnInactive = true;
    }

    @Deprecated
    public Transfer(ChannelHandlerContext target) {
        this.target = target.channel();
        closeOnInactive = true;
    }

    /**
     * 将读到的数据写到 target 中
     *
     * @param target          target
     * @param closeOnInactive channelInactive 事件触发时关闭 target，默认为true
     */
    public Transfer(Channel target, boolean closeOnInactive) {
        this.target = target;
        this.closeOnInactive = closeOnInactive;
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        if (closeOnInactive && target.isActive())
            target.close();

        super.channelInactive(ctx);
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
//        ByteBuf buf = (ByteBuf) msg;
//        CharSequence charSequence = buf.getCharSequence(0, buf.readableBytes(), StandardCharsets.UTF_8);
//        System.out.println(target.channel().remoteAddress() + "====>\n" + charSequence);
        target.writeAndFlush(msg);
    }
}
