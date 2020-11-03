package org.jd.net.http.proxy;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import org.apache.commons.lang3.RandomUtils;
import org.jd.net.netty.Buf;

import java.nio.charset.StandardCharsets;

public class XorCodec extends ChannelDuplexHandler {
    private final byte[] password;
    private int decodeIndex = 0, encodeIndex = 0;

    private volatile boolean headCodec = true;//头部已处理
    private boolean[] headPassword = new boolean[]{
            false, false, true, true, true, false, false, true, false, true, true,
            true, false, false, true, true, true, false, false, true, true, false,
            false, true, false, true, false, false, true, false, false, true, false,
            true, false, false, false, false, false, true, false, false, false, true,
            false, true, false, true, false, true, false, true, false, false, true,
    };

    public XorCodec(String password) {
        this.password = password.getBytes(StandardCharsets.US_ASCII);
    }

    @Override
    public synchronized void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {

        ByteBuf buf = (ByteBuf) msg;
        decodeIndex = codec(buf, decodeIndex);

        if (headCodec) {//去掉头部
            for (int i = 0; i < 10; i++) {
                byte b = buf.readByte();
                if (b < 0 || b > headPassword.length || headPassword[b]) {
                    buf.release();
                    throw new IllegalArgumentException("密钥无效");
                }
            }
            headCodec = false;
        }
        ctx.fireChannelRead(buf);
    }

    @Override
    public synchronized void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
        ByteBuf buf = (ByteBuf) msg;
        if (headCodec) {//添加头部
            ByteBuf head = Buf.alloc(10);
            while (head.isWritable()) {
                int headByte;
                do {
                    headByte = RandomUtils.nextInt(0, headPassword.length);
                } while (headPassword[headByte]);
                head.writeByte(headByte);
            }
            buf = Buf.alloc.compositeBuffer(2).addComponents(true, head, buf);
            headCodec = false;
        }
        encodeIndex = codec(buf, encodeIndex);
        ctx.write(buf, promise);
    }

    /**
     *
     * @param buf
     * @param passwordIndex
     * @return
     */
    private int codec(ByteBuf buf, int passwordIndex) {
        byte[] b = new byte[buf.readableBytes()];
        buf.readBytes(b);
        for (int i = 0; i < b.length; i++) {
            if (passwordIndex == password.length)
                passwordIndex = 0;
            b[i] = (byte) (b[i] ^ password[passwordIndex++]);
        }
        buf.clear().writeBytes(b);
        return passwordIndex;
    }

}
