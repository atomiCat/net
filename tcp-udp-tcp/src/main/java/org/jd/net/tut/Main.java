package org.jd.net.tut;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.channel.socket.DatagramPacket;
import org.jd.net.core.Buf;
import org.jd.net.core.Netty;

import java.net.InetSocketAddress;
import java.util.HashMap;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;

public class Main {
    public static void main(String[] a) {

    }

    /**
     * 启动客户端
     *
     * @param tcpListenPort 客户端tcp监听端口
     * @param host          服务端
     * @param port          服务端
     */
    static void client(int tcpListenPort, String host, int port) {
        ConcurrentHashMap<Integer, Channel> tcpMap = new ConcurrentHashMap<>();
        Channel udpServer = Netty.udp(port, new ChannelDuplexHandler() {
            @Override
            public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
                ctx.write(new DatagramPacket((ByteBuf) msg, new InetSocketAddress(host, port)), promise);
            }

            @Override
            public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
                ByteBuf buf = ((DatagramPacket) msg).content();
                Channel tcp = tcpMap.get(buf.readInt());
                if (tcp != null) {
                    tcp.writeAndFlush(buf);
                } else {
                    buf.release();
                }
            }
        }).syncUninterruptibly().channel();

        AtomicInteger integer = new AtomicInteger(0);
        Netty.accept(tcpListenPort, new ChannelInitializer<Channel>() {
            @Override
            protected void initChannel(Channel ch) throws Exception {
                int index = integer.getAndAdd(1);
                tcpMap.put(index, ch);
                ch.pipeline().addLast(new ChannelInboundHandlerAdapter() {
                    @Override
                    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
                        udpServer.writeAndFlush(Unpooled.compositeBuffer(2).addComponents(true, Buf.wrap(index), (ByteBuf) msg));
                    }
                });
            }
        }).syncUninterruptibly().channel().closeFuture().syncUninterruptibly();
    }

    /**
     * 启动服务端
     *
     * @param udpListenPort 服务端udp监听端口
     * @param host
     * @param port
     */
    static void server(int udpListenPort, String host, int port) {

    }
}
