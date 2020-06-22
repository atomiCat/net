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
        if (msg instanceof ByteBuf)
            msg = new DatagramPacket((ByteBuf) msg, remote);
        super.write(ctx, msg, promise);
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        DatagramPacket datagramPacket = (DatagramPacket) msg;
        if (remote == null)
            remote = datagramPacket.sender();
        ByteBuf buf = (datagramPacket).content();
        write2Tcp(buf);
    }

    final Logger logger = LoggerFactory.getLogger(getClass());

    protected void write2Tcp(ByteBuf buf) {
        int channelIndex = buf.readInt();
        logger.info("写入数据->tcp channelIndex {} daaIndex {}", channelIndex, buf.getInt(buf.readerIndex()));
        Channel tcp = tcpMap.get(channelIndex);//根据channelIndex选择合适的tcp连接
        if (tcp != null) {
            tcp.writeAndFlush(buf);
        } else {
            logger.warn("写入tcp 失败！");
            buf.release();
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        logger.error("异常", cause);
        ctx.close();
        ctx.fireExceptionCaught(cause);
        System.exit(0);
    }
}
