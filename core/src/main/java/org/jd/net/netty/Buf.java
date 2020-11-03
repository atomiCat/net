package org.jd.net.netty;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.PooledByteBufAllocator;
import org.apache.commons.lang3.CharSet;
import org.apache.commons.lang3.CharSetUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;

/**
 * ByteBuf Utils
 */
public interface Buf {
    ByteBufAllocator alloc = PooledByteBufAllocator.DEFAULT;

    static String toString(ByteBuf buf) {
        byte[] bytes = new byte[buf.readableBytes()];
        buf.getBytes(buf.readerIndex(), bytes);
        return new String(bytes);
    }

    static ByteBuf alloc(int maxSize) {
        return alloc.buffer(maxSize, maxSize);
    }

    static ByteBuf wrapByte(int i) {
        return alloc(1).writeByte(i);
    }

    static ByteBuf wrap(CharSequence... s) {
        int size = 0;
        for (CharSequence c : s)
            size += c.length();
        ByteBuf buf = alloc.buffer(size);
        for (CharSequence c : s)
            buf.writeCharSequence(c, StandardCharsets.US_ASCII);
        return buf;
    }

    static ByteBuf wrap(int i) {
        return alloc(4).writeInt(i);
    }

//    static ByteBuf wrap(long l) {
//        return alloc(8).writeLong(l);
//    }

    static ByteBuf wrap(int i1, int i2) {
        return alloc(8).writeInt(i1).writeInt(i2);
    }

    static String readString(ByteBuf buf) {
        return buf.readCharSequence(buf.readableBytes(), StandardCharsets.UTF_8).toString();
    }
}
