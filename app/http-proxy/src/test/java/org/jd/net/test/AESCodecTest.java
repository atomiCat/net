package org.jd.net.test;

import io.netty.buffer.ByteBuf;
import org.jd.net.http.proxy.AESCodec;
import org.jd.net.netty.Buf;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.Cipher;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;

public class AESCodecTest {
    static Logger logger = LoggerFactory.getLogger(AESCodecTest.class);

    public static void main(String[] a) throws GeneralSecurityException {
        AESCodec codec = new AESCodec("123456");
        ByteBuf encrypted = codec.codec(Buf.wrap("hello word"), Cipher.ENCRYPT_MODE);
        ByteBuf decrypted = codec.codec(encrypted, Cipher.DECRYPT_MODE);
        String s = decrypted.toString(StandardCharsets.UTF_8);
        logger.info(s);
    }

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
