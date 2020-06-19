package org.jd.net.tut;

import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.channel.socket.DatagramPacket;

import java.net.InetSocketAddress;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 添加到udp pipeline中
 * 将读到的udp包readInt作为index，从tcpMap中取出tcp连接 并写入数据
 */
public class UdpTcpTransfer extends ChannelDuplexHandler {
    private final InetSocketAddress remote;
    private final ConcurrentHashMap<Integer, Channel> tcpMap;

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
        ByteBuf buf = ((DatagramPacket) msg).content();
        Channel tcp = tcpMap.get(buf.readInt());//根据channelIndex选择合适的tcp连接
        if (tcp != null) {
            tcp.writeAndFlush(buf);
        } else {
            buf.release();
        }
    }
}
