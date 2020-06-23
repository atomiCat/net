package org.jd.net.tut;

import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.channel.socket.DatagramPacket;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 添加到udp pipeline中
 * 将读到的udp包readInt作为index，从tcpMap中取出tcp连接 并写入数据,不保证顺序
 */
public class UdpTcpTransfer extends ChannelDuplexHandler {
    private InetSocketAddress remote;
    protected final ConcurrentHashMap<Integer, Channel> tcpMap;

    public UdpTcpTransfer(InetSocketAddress remote, ConcurrentHashMap<Integer, Channel> tcpMap) {
        this.remote = remote;
        this.tcpMap = tcpMap;
    }

    @Override
    public void handlerAdded(ChannelHandlerContext ctx) throws Exception {
        //添加防丢包处理器
        ctx.pipeline().addBefore(ctx.name(), null, new PacketLostHandler());
        super.handlerAdded(ctx);
    }

    @Override
    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
        ByteBuf buf = (ByteBuf) msg;
        while (buf.readableBytes() > 1024) {
            ctx.write(new DatagramPacket(buf.readRetainedSlice(1024), remote), promise);
        }
        ctx.write(new DatagramPacket(buf, remote), promise);
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        DatagramPacket datagramPacket = (DatagramPacket) msg;
        if (remote == null)
            remote = datagramPacket.sender();
        ByteBuf buf = (datagramPacket).content();
        int index = buf.readInt();//channelIndex
        if (ClosedChannelMarker.isClosed(index)) {
            if (buf.readableBytes() > 4)//==4说明是关闭信号
                logger.info("write2Tcp 失败，tcp已关闭 channelIdx:{} dataIdx:{} readable:{}", index, buf.readInt(), buf.readableBytes());
            buf.release();
            return;
        }
        write2Tcp(buf, index);
    }

    final Logger logger = LoggerFactory.getLogger(getClass());

    private void write2Tcp(ByteBuf buf, int channelIndex) {
        logger.info("写入数据->tcp channelIndex {} dataIndex {}", channelIndex, buf.getInt(buf.readerIndex()));
        Channel tcp = getTcpChannel(channelIndex);//根据channelIndex选择合适的tcp连接
        if (tcp != null) {
            tcp.writeAndFlush(buf);
        } else {
            throw new IllegalStateException("写入数据->tcp异常:tcp==null");
        }
    }

    protected Channel getTcpChannel(Integer channelIndex) {
        return tcpMap.get(channelIndex);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        logger.error("异常", cause);
        ctx.channel().close();
        ctx.fireExceptionCaught(cause);
        System.exit(0);
    }
}
