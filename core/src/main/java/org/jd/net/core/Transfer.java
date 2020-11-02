package org.jd.net.core;

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;

public class Transfer extends ChannelInboundHandlerAdapter {
    private final Channel target;
    private final boolean closeOnInactive;
    private final boolean autoReadOnActive;

    public Transfer(Channel target) {
        this(target, false, true);
    }

    /**
     * 将读到的数据写到 target 中
     *
     * @param target           target
     * @param autoReadOnActive channelActive 事件触发时调用 target.config().setAutoRead(true)  默认为false
     * @param closeOnInactive  channelInactive 事件触发时调用 target.close() ，默认为true
     */
    public Transfer(Channel target, boolean autoReadOnActive, boolean closeOnInactive) {
        this.target = target;
        this.autoReadOnActive = autoReadOnActive;
        this.closeOnInactive = closeOnInactive;
    }


    public Transfer(Channel target, boolean autoReadOnActive) {
        this(target, autoReadOnActive, true);
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        target.config().setAutoRead(true);
        super.channelActive(ctx);
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
