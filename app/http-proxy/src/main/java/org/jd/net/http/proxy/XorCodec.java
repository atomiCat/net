package org.jd.net.http.proxy;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import org.jd.net.netty.Buf;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HashMap;

/**
 * 异或加解密，根据给定字符串生成1024字节密钥，与明文数据异或。
 */
public class XorCodec extends ChannelDuplexHandler {
    /**
     * 通过明文密钥不断MD5生成一个长字节数组用来异或加解密
     */
    public static byte[] genPassword(String password) {
        ByteBuf passwordBytes = Buf.alloc.heapBuffer(1024);//通过MD5算法生成2048字节长度的密钥用来异或
        try {
            MessageDigest digest = MessageDigest.getInstance("MD5");
            byte[] bytes = password.getBytes(StandardCharsets.UTF_8);
            for (int i = 0; passwordBytes.isWritable(); i++) {
                bytes = digest.digest(bytes);
                if (i > 13)//从第13次MD5开始算有效密钥
                    passwordBytes.writeBytes(bytes, 0, Math.min(bytes.length, passwordBytes.writableBytes()));
            }
            return passwordBytes.array();//上面分配的是heapBuffer，所以此处不会抛异常
        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            passwordBytes.release();
        }
    }

    private final byte[] password;
    private int decodeIndex = 0, encodeIndex = 0;
    private static final HashMap<String, byte[]> CACHE = new HashMap<>();

    public XorCodec(String passwordStr) {
        this.password = CACHE.computeIfAbsent(passwordStr, s -> genPassword(passwordStr));
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        ByteBuf buf = (ByteBuf) msg;
        decodeIndex = codec(buf, decodeIndex);
        ctx.fireChannelRead(buf);
    }

    @Override
    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) {
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
