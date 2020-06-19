package org.jd.net.tut;

import io.netty.buffer.ByteBuf;
import io.netty.channel.*;
import io.netty.channel.socket.DatagramPacket;
import org.jd.net.core.Netty;

import java.net.InetSocketAddress;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class Main {
    public static void main(String[] a) {
        client(2000, "127.0.0.1", 2001);
    }

    /**
     * 启动客户端
     *
     * @param tcpListenPort 客户端tcp监听端口
     * @param host          udp服务端 host
     * @param port          udp服务端 port
     */
    static void client(int tcpListenPort, String host, int port) {
        ConcurrentHashMap<Integer, Channel> tcpMap = new ConcurrentHashMap<>();
        Channel udpServer = Netty.udp(port, new UdpTcpTransfer(new InetSocketAddress(host, port), tcpMap))
                .syncUninterruptibly().channel();

        Netty.accept(tcpListenPort, new ChannelInitializer<Channel>() {
            @Override
            protected void initChannel(Channel ch) throws Exception {
                ch.pipeline().addLast(new TcpUdpTransfer(tcpMap, udpServer));
            }
        }).syncUninterruptibly().channel().closeFuture().syncUninterruptibly();
    }

    /**
     * 启动服务端
     *
     * @param udpListenPort 服务端udp监听端口
     * @param host          tcp 服务端 host
     * @param port          tcp 服务端 port
     */
    static void server(int udpListenPort, String host, int port) {
        ConcurrentHashMap<Integer, Channel> tcpMap = new ConcurrentHashMap<>();
        Channel udpServer = Netty.udp(udpListenPort, new ).syncUninterruptibly().channel();
    }
}
