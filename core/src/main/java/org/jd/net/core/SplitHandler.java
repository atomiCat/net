package org.jd.net.core;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.CompositeByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;

public class SplitHandler extends ChannelInboundHandlerAdapter {
    static final Logger logger = LoggerFactory.getLogger(SplitHandler.class);
    private final byte[] separator;

    public SplitHandler(byte... separator) {
        if (separator == null || separator.length == 0)
            throw new IllegalArgumentException("separator is not allowed to be null");
        this.separator = separator;
    }

    public SplitHandler(String separator) {
        this(separator.getBytes(StandardCharsets.US_ASCII));
    }

    private CompositeByteBuf bufs;

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        split(ctx, (ByteBuf) msg);
    }

    protected void split(ChannelHandlerContext ctx, ByteBuf buffer) throws Exception {
        int iEnd = findIndexOfSeparatorEnd(buffer, buffer.readerIndex());

        if (iEnd < 0) {//此次读取没有分隔符
            (bufs == null ? (bufs = Buf.alloc.compositeBuffer()) : bufs).addComponent(true, buffer);
            return;
        }

        int frameLength = iEnd + 1 - buffer.readerIndex();//一帧的字节数，包括分隔符长度
        ByteBuf frame = buffer.readRetainedSlice(frameLength);
        buffer.readerIndex(iEnd + 1);
        if (bufs != null) {
            bufs.addComponent(true, frame);
            frame = bufs;
            bufs = null;
        }
        decodeFrame(ctx, frame, buffer);

        if (buffer.isReadable() && buffer.refCnt() > 0) {
            split(ctx, buffer);
        }
    }

    /**
     * 处理其中一帧
     *
     * @param ctx
     * @param split   帧
     * @param remains 此帧后面剩余数据
     * @throws Exception
     */
    protected void decodeFrame(ChannelHandlerContext ctx, ByteBuf split, ByteBuf remains) throws Exception {
        if (!remains.isReadable())
            remains.release();
        ctx.fireChannelRead(split);
    }

    /**
     * 在buffer中查找separator最后一字节的位置
     * 并向前对比separator序列是否一致
     *
     * @param buffer
     * @param startIndex
     * @return
     */
    private int findIndexOfSeparatorEnd(ByteBuf buffer, int startIndex) {
        int iEnd = buffer.indexOf(startIndex, buffer.writerIndex(), separator[separator.length - 1]);
        if (iEnd >= 0) {
            for (int bi = iEnd - 1, si = separator.length - 2; si >= 0; bi--, si--) {
                if (bi >= buffer.readerIndex()) {
                    if (separator[si] != buffer.getByte(bi))
                        return findIndexOfSeparatorEnd(buffer, iEnd + 1);
                } else {
                    if (bufs == null || bufs.getByte(bufs.writerIndex() + bi - buffer.readerIndex()) != separator[si])
                        return findIndexOfSeparatorEnd(buffer, iEnd + 1);
                }
            }
            return iEnd;
        }
        return -1;
    }
}
