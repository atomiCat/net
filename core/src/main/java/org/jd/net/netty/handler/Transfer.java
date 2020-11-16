package org.jd.net.netty.handler;

import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import org.jd.net.netty.Buf;

public class Transfer extends TargetDoOn.Adapter {
    private Transfer(Channel target, int... someThings) {
        super(target, someThings);
    }

    /**
     * 将读到的数据写到 target 中
     * 并在 channelInactive 事件发生时，调用target.close() 关闭target
     *
     * @see #closeOnInactive
     */
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

    private ByteBuf headData;

    /**
     * writeOnActive
     * @param bufs channelActive事件发生时将 bufs 写入 target
     * @return
     */
    public Transfer writeOnActive(ByteBuf... bufs) {
        if (headData != null)
            throw new IllegalStateException("writeOnActive has already been called");

        if (bufs != null)
            headData = bufs.length > 1 ? Buf.alloc.compositeBuffer(bufs.length).addComponents(true, bufs) : bufs[0];
        return this;
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        if (headData != null) {
            target.writeAndFlush(headData);
            headData = null;
        }

        super.channelActive(ctx);
    }

    @Override
    protected void finalize() throws Throwable {
        if (headData != null)
            headData.release();
        super.finalize();
    }
}
