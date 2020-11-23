package org.jd.net.http.proxy;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import org.jd.net.netty.Buf;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.Arrays;

/**
 * AES加解密，根据给定字符串生成16字节128位密钥，进行AES加解密
 * 加密模式ECB 填充模式PKCS5Padding
 */
public class AESCodec extends ChannelDuplexHandler {
    private final byte[] key;

    public AESCodec(String password) {
        try {
            MessageDigest digest = MessageDigest.getInstance("MD5");
            byte[] bytes = password.getBytes(StandardCharsets.UTF_8);
            for (int i = 0; i < 17; i++) {
                bytes = digest.digest(bytes);
            }
            //AES key 128位 = 16字节
            this.key = Arrays.copyOf(bytes, 16);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws GeneralSecurityException {
        ByteBuf out = codec((ByteBuf) msg, Cipher.DECRYPT_MODE);
        ctx.fireChannelRead(out);
    }

    @Override
    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws GeneralSecurityException {
        ByteBuf out = codec((ByteBuf) msg, Cipher.ENCRYPT_MODE);
        ctx.write(Buf.wrap(out.readableBytes()));//写入一个int值表示密文长度
        ctx.write(out, promise);
    }

    @Override
    public void handlerAdded(ChannelHandlerContext ctx) throws Exception {
        //每一段完整的密文前都有一个 int 值表示该段密文长度，使用 LengthFieldBasedFrameDecoder 来保证读到的密文完整性
        ctx.pipeline().addBefore(ctx.name(), "LengthFieldBasedFrameDecoder",
                new LengthFieldBasedFrameDecoder(Integer.MAX_VALUE, 0, 4, 0, 4));
        super.handlerAdded(ctx);
    }

    private final Cipher[] codec = new Cipher[3];//codec[1]为编码器;codec[2]为解码器

    /**
     * 数据编解码
     *
     * @param in     输入数据
     * @param opMode 编解码模式，必须为 {@link Cipher#ENCRYPT_MODE} 或 {@link Cipher#DECRYPT_MODE}
     * @return output
     */
    public ByteBuf codec(ByteBuf in, int opMode) throws GeneralSecurityException {
        if (codec[opMode] == null) {//初始化 Cipher
            codec[opMode] = Cipher.getInstance("AES/ECB/PKCS5Padding");
            codec[opMode].init(opMode, new SecretKeySpec(key, "AES"));
        }

        int paddingSize = opMode == Cipher.ENCRYPT_MODE ? 16 : 0;//加密时明文需要补齐，解密无需补齐
        ByteBuf outBuf = Buf.alloc(in.readableBytes() + paddingSize);
        ByteBuffer outBuffer = outBuf.nioBuffer(0, outBuf.capacity());
        codec[opMode].doFinal(in.nioBuffer(), outBuffer);//编码
        outBuf.writerIndex(outBuffer.position());//编码后的长度
//        log.info("{} in:{} out:{}", opMode == Cipher.ENCRYPT_MODE ? "加密" : "解密", in.readableBytes(), outBuf.readableBytes());
        in.release();
        return outBuf;
    }

//    private static final Logger log = LoggerFactory.getLogger(AESCodec.class);


}
