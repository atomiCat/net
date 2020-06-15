package org.jd.net.tut;

import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.socket.DatagramPacket;
import org.jd.net.core.Netty;
import org.jd.net.core.rudp.DuplexTransferRUDP;

import java.net.InetSocketAddress;
import java.util.HashMap;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

public class Main {
    public static void main(String[] a) {

    }

    static void tcp2udp(int listenPort, String host, int port) {
        Netty.accept(listenPort, new ChannelInitializer<Channel>() {
            @Override
            protected void initChannel(Channel ch) throws Exception {
                ch.pipeline().addLast(new DuplexTransferRUDP(new InetSocketAddress(host, port)));
            }
        }).syncUninterruptibly().channel().closeFuture().syncUninterruptibly();
    }

    static void udp2tcp(int listenPort, String host, int port) {
        HashMap<InetSocketAddress, BlockingQueue<ByteBuf>> map = new HashMap<>();
        Netty.udp(listenPort, new ChannelInboundHandlerAdapter() {
            @Override
            public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
                DatagramPacket packet = (DatagramPacket) msg;
                InetSocketAddress sender = packet.sender();
                map.computeIfAbsent(sender, address -> {

                    return new LinkedBlockingQueue<>();
                }).put(packet.content());

            }
        });
    }

    static class TcpBuf {
//        LinkedList<ByteBuf> bufs
    }
}
