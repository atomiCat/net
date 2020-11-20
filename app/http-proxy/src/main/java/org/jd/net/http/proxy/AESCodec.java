package org.jd.net.http.proxy;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import org.jd.net.netty.Buf;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.Arrays;

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

    private Cipher decoder;

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws GeneralSecurityException {
        ByteBuf buf = (ByteBuf) msg;
        if (decoder == null)
            decoder = init(Cipher.DECRYPT_MODE);

    }

    private Cipher encoder;

    /**
     *
     */
    @Override
    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws GeneralSecurityException {
        ByteBuf buf = (ByteBuf) msg;
        if (encoder == null)
            encoder = init(Cipher.ENCRYPT_MODE);
        ByteBuf out = Buf.alloc(buf.readableBytes() + 4);
        out.setInt(0, buf.readableBytes());

        ByteBuffer outBuffer = out.nioBuffer();
        outBuffer.clear();
        outBuffer.position()
        encoder.doFinal(buf.nioBuffer(), );
    }

    /**
     * 初始化 Cipher
     */
    private Cipher init(int opmode) throws GeneralSecurityException {
        Cipher c = Cipher.getInstance("AES/ECB/PKCS5Padding");
        c.init(opmode, new SecretKeySpec(key, "AES"));
        return c;
    }

//    static Logger log = LoggerFactory.getLogger("");

//    public static void main(String[] a) throws Exception {
//        byte[] text = new byte[15];
//        byte[] key = new byte[16];
//        Cipher c = Cipher.getInstance("AES/ECB/PKCS5Padding");
//        SecretKey deskey = new SecretKeySpec(key, "AES");
//        c.init(Cipher.ENCRYPT_MODE, deskey);
//        log.info("开始加密");
//        byte[] bytes = c.doFinal(text);//加密
//        log.info("加密完毕 {}", bytes.length);
//
//        Cipher c2 = Cipher.getInstance("AES/ECB/PKCS5Padding");
//        SecretKey deskey2 = new SecretKeySpec(key, "AES");
//        c2.init(Cipher.DECRYPT_MODE, deskey2);
//        log.info("开始解密");
//        byte[] bytes1 = c2.doFinal(bytes);
//        log.info("解密完毕");
//        System.out.println("ok");
//
//
//    }
}
