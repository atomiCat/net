package org.jd.net.tut;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.CompositeByteBuf;
import io.netty.buffer.EmptyByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import org.jd.net.core.Buf;

import java.util.Iterator;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 添加到 tcp pipeline中
 * 将读到的 tcp 包添加channelIndex和dataIndex，通过ucp发送出去
 */
public class TcpUdpTransfer extends ChannelDuplexHandler {
    private static final AtomicInteger channelIndexFactor = new AtomicInteger(0);
    private final int channelIndex = channelIndexFactor.getAndAdd(1);
    private final ConcurrentHashMap<Integer, Channel> tcpMap;
    private final Channel udp;

    public TcpUdpTransfer(ConcurrentHashMap<Integer, Channel> tcpMap, Channel udp) {
        this.tcpMap = tcpMap;
        this.udp = udp;
    }

    @Override
    public void handlerAdded(ChannelHandlerContext ctx) throws Exception {
        tcpMap.put(channelIndex, ctx.channel());
        super.handlerAdded(ctx);
    }

    private final AtomicInteger dataIndexFactor = new AtomicInteger(0);

    @Override//添加channelIndex 和 dataIndex
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        writeToUdp((ByteBuf) msg);
    }

    private void writeToUdp(ByteBuf buf) {
        CompositeByteBuf cbuf = Unpooled.compositeBuffer(2).
                addComponents(true,
                        Buf.wrap(channelIndex, dataIndexFactor.getAndAdd(1)),
                        buf
                );
        udp.writeAndFlush(cbuf);
    }

    private boolean channelNotActive = true;

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        channelNotActive = false;
        if (!indexBufs.isEmpty())
            write(ctx, null, null);
        super.channelActive(ctx);
    }

    TreeSet<IndexBuf> indexBufs = new TreeSet<>();
    private int lastConsumedIndex = -1;

    @Override//将udp写来的包排序
    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
        if (msg instanceof ByteBuf) {
            ByteBuf buf = (ByteBuf) msg;
            indexBufs.add(new IndexBuf(buf.readInt(), buf));
        }
        if (channelNotActive)
            return;
        //将indexBufs数据发送到tcp
        Iterator<IndexBuf> iterator = indexBufs.iterator();
        while (iterator.hasNext()) {
            IndexBuf buf = iterator.next();
            if (buf.index == lastConsumedIndex + 1) {
                if (buf.byteBuf.isReadable()) {
                    if (promise == null)
                        ctx.write(buf.byteBuf);
                    else
                        ctx.write(buf.byteBuf, promise);
                } else {
                    ctx.flush();
                    buf.byteBuf.release();
                    ctx.close();
                }
                lastConsumedIndex = buf.index;
                iterator.remove();
            }
        }
    }

    /**
     * 清理资源
     */
    public void close() {
        tcpMap.remove(channelIndex);
        if (!indexBufs.isEmpty()) {
            indexBufs.forEach(indexBuf -> indexBuf.byteBuf.release());
        }
        writeToUdp(new EmptyByteBuf(Buf.alloc));//通知对方端数据结尾
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        close();
        super.channelInactive(ctx);
    }
}
