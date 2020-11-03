package org.jd.net.netty.handler;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.CompositeByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import org.jd.net.netty.Buf;

import java.nio.charset.StandardCharsets;

/**
 * 按照指定分隔符分割数据
 */
public class SplitHandler extends ChannelInboundHandlerAdapter {
    private final byte[] separator;//分隔符

    public SplitHandler(byte... separator) {
        if (separator == null || separator.length == 0)
            throw new IllegalArgumentException("separator is not allowed to be null");
        this.separator = separator;
    }

    public SplitHandler(String separator) {
        this(separator.getBytes(StandardCharsets.US_ASCII));
    }

    /**
     * 缓存接收到的数据
     */
    private CompositeByteBuf bufs;

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        split(ctx, (ByteBuf) msg);
    }

    /**
     * 分割数据，处理粘包和合并数据
     *
     * @param ctx
     * @param buffer
     * @throws Exception
     */
    protected void split(ChannelHandlerContext ctx, ByteBuf buffer) throws Exception {
        int iEnd = findIndexOfSeparatorEnd(buffer, buffer.readerIndex());

        if (iEnd < 0) {//此次读取没有分隔符
            (bufs == null ? (bufs = Buf.alloc.compositeBuffer()) : bufs).addComponent(true, buffer);
            if (bufs.readableBytes() > 8192)
                throw new IllegalStateException("协议错误！");
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

        //若有还数据则递归调用继续处理
        if (buffer.isReadable() && buffer.refCnt() > 0) {
            split(ctx, buffer);
        }
    }

    /**
     * 处理其中一帧
     * 根据需要重写此方法
     *
     * @param split   分割完的帧，包含分隔符
     * @param remains 此帧后面剩余数据
     * @throws Exception
     */
    protected void decodeFrame(ChannelHandlerContext ctx, ByteBuf split, ByteBuf remains) throws Exception {
        if (!remains.isReadable())
            remains.release();
        ctx.fireChannelRead(split);
    }

    /**
     * 在buffer中从startIndex位置开始向后查找separator最后一字节的位置
     * 并向前检查separator序列是否一致
     * 如果向前检查过程中已经检查到buffer.readerIndex，则从bufs末尾继续向前检查
     *
     * @param startIndex 从此位置开始查找
     * @return separator最后一字节的位置
     */
    private int findIndexOfSeparatorEnd(ByteBuf buffer, int startIndex) {
        int iEnd = buffer.indexOf(startIndex, buffer.writerIndex(), separator[separator.length - 1]);
        if (iEnd < 0)
            return -1;
        //       bi:bufferIndex si:separatorIndex
        for (int bi = iEnd - 1, si = separator.length - 2; si >= 0; bi--, si--) {
            if (bi >= buffer.readerIndex()) {
                if (separator[si] != buffer.getByte(bi))//检查未通过，向后继续查找
                    return findIndexOfSeparatorEnd(buffer, iEnd + 1);
            } else {//如果向前检查过程中已经检查到buffer.readerIndex，则从bufs末尾继续向前检查
                if (bufs == null)
                    return findIndexOfSeparatorEnd(buffer, iEnd + 1);
                int bufsIndex = bufs.writerIndex() + bi - buffer.readerIndex();
                if (bufsIndex < bufs.readerIndex() || bufs.getByte(bufsIndex) != separator[si])
                    return findIndexOfSeparatorEnd(buffer, iEnd + 1);
            }
        }
        return iEnd;
    }
}
