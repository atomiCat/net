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

import java.util.HashSet;
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

    TreeSet<IndexBuf> indexBufs = new TreeSet<>();

    @Override//将udp写来的包排序
    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
        ByteBuf buf = (ByteBuf) msg;
        indexBufs.add(new IndexBuf(buf.readInt(), buf));
        HashSet<IndexBuf> toRemove = new HashSet<>();
        IndexBuf that = null;
        for (IndexBuf next : indexBufs) {
            if (that != null) {
                if (that.index + 1 == next.index) {
                    ctx.write(that.byteBuf, promise);
                    toRemove.add(that);
                } else {
                    break;
                }
            }
            that = next;
            if (that.byteBuf.readableBytes() == 0) {//最后一个数据包
                ctx.flush();
                that.byteBuf.release();
                ctx.close();
            }
        }
        indexBufs.removeAll(toRemove);//移除已消费
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        tcpMap.remove(channelIndex);
        writeToUdp(new EmptyByteBuf(Buf.alloc));//通知服务端数据结尾
        super.channelInactive(ctx);
    }
}
