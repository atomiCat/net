package org.jd.net.http.proxy;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

public class AESCodec extends ChannelDuplexHandler {
    private final byte[] password;

    public AESCodec(byte[] password) {
        this.password = password;
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {

    }

    @Override
    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) {

    }


    static Logger log = LoggerFactory.getLogger("");

    public static void main(String[] a) throws Exception {
        byte[] text = new byte[15];
        byte[] key = new byte[16];
        Cipher c = Cipher.getInstance("AES/ECB/PKCS5Padding");
        SecretKey deskey = new SecretKeySpec(key, "AES");
        c.init(Cipher.ENCRYPT_MODE, deskey);
        log.info("开始加密");
        byte[] bytes = c.doFinal(text);//加密
        log.info("加密完毕 {}", bytes.length);

        Cipher c2 = Cipher.getInstance("AES/ECB/PKCS5Padding");
        SecretKey deskey2 = new SecretKeySpec(key, "AES");
        c2.init(Cipher.DECRYPT_MODE, deskey2);
        log.info("开始解密");
        byte[] bytes1 = c2.doFinal(bytes);
        log.info("解密完毕");
        System.out.println("ok");


    }
}
