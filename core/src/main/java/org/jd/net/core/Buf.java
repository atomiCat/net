package org.jd.net.core;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.PooledByteBufAllocator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;

public class Buf {
    static Logger logger = LoggerFactory.getLogger(Buf.class);
    public static ByteBufAllocator alloc = PooledByteBufAllocator.DEFAULT;

    public static void print(String title,ByteBuf buf) {
        byte[] bytes = new byte[buf.readableBytes()];
        buf.getBytes(buf.readerIndex(), bytes);
//        logger.info("buf {} 内容：{}",title,bytes);
        logger.info("buf {} 内容：{}",title,new String(bytes));
    }

    public static ByteBuf alloc(int maxSize) {
        return alloc.buffer(maxSize, maxSize);
    }

    public static ByteBuf wrapByte(int i) {
        return alloc(1).writeByte(i);
    }

    public static ByteBuf wrap(CharSequence... s) {
        int size = 0;
        for (CharSequence c : s)
            size += c.length();
        ByteBuf buf = alloc.buffer(size, size * 4);
        for (CharSequence c : s)
            buf.writeCharSequence(c, StandardCharsets.UTF_8);
        return buf;
    }

    public static ByteBuf wrap(int i) {
        return alloc(4).writeInt(i);
    }

    public static ByteBuf wrap(long l) {
        return alloc(8).writeLong(l);
    }

    public static ByteBuf wrap(int i1, int i2) {
        return alloc(8).writeInt(i1).writeInt(i2);
    }

    public static String readString(ByteBuf buf) {
        return buf.readCharSequence(buf.readableBytes(), StandardCharsets.UTF_8).toString();
    }

//
//    public static ByteBuf wrap(Object... objects) {
//        ByteBuf buffer = alloc.buffer();
//        for (Object data : objects) {
//            if (data instanceof CharSequence)
//                buffer.writeCharSequence((CharSequence) data, StandardCharsets.UTF_8);
//            else if (data instanceof Integer) {
//                buffer.writeInt((Integer) data);
//            }
//        }
//        return buffer;
//    }
}
