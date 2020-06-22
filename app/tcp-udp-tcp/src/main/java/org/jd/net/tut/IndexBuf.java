package org.jd.net.tut;

import io.netty.buffer.ByteBuf;

public class IndexBuf implements Comparable<IndexBuf> {
    public final Integer index;
    public final ByteBuf byteBuf;

    public IndexBuf(int index, ByteBuf byteBuf) {
        this.index = index;
        this.byteBuf = byteBuf;
    }

    @Override
    public int compareTo(IndexBuf o) {
        return index.compareTo(o.index);
    }

    @Override
    public boolean equals(Object obj) {
        return index.equals(((IndexBuf) obj).index);
    }

    @Override
    public int hashCode() {
        return index.hashCode();
    }
}
