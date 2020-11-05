package org.jd.net.http.proxy;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;

public class XorCodec extends ChannelDuplexHandler {
    private final byte[] password;
    private int decodeIndex = 0, encodeIndex = 0;

    public XorCodec(byte[] password) {
        this.password = password;
    }

    @Override
    public synchronized void channelRead(ChannelHandlerContext ctx, Object msg) {
        ByteBuf buf = (ByteBuf) msg;
        decodeIndex = codec(buf, decodeIndex);
        ctx.fireChannelRead(buf);
    }

    @Override
    public synchronized void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) {
        ByteBuf buf = (ByteBuf) msg;
        encodeIndex = codec(buf, encodeIndex);
        ctx.write(buf, promise);
    }

    /**
     * 异或编解码
     *
     * @param passwordIndex password 数组坐标
     */
    private int codec(ByteBuf buf, int passwordIndex) {
        byte[] b = new byte[buf.readableBytes()];
        buf.readBytes(b);
        for (int i = 0; i < b.length; i++) {
            if (passwordIndex == password.length)//密钥用完从头开始
                passwordIndex = 0;
            b[i] = (byte) (b[i] ^ password[passwordIndex++]);
        }
        buf.clear().writeBytes(b);
        return passwordIndex;
    }

}
