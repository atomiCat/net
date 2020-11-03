package org.jd.net.netty.handler;

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;

/**
 * 将读到的数据写到 target 中
 */
public class Transfer extends TargetDoOn.Adapter {
    private Transfer(Channel target, int... someThings) {
        super(target, someThings);
    }

    public Transfer(Channel target) {
        this(target, closeOnInactive);
    }

    /**
     * 当 channelActive 发生时调用 target.config().setAutoRead(true)
     */
    public static Transfer autoReadOnActive(Channel target) {
        return new Transfer(target, closeOnInactive, setAutoReadOnActive);
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
//        ByteBuf buf = (ByteBuf) msg;
//        CharSequence charSequence = buf.getCharSequence(0, buf.readableBytes(), StandardCharsets.UTF_8);
//        System.out.println(target.channel().remoteAddress() + "====>\n" + charSequence);
        target.writeAndFlush(msg);
    }
}
