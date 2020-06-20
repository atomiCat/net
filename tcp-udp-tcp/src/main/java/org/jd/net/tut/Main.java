package org.jd.net.tut;

import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import org.jd.net.core.Netty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.util.concurrent.ConcurrentHashMap;

public class Main {
    static Logger logger = LoggerFactory.getLogger(Main.class);

    public static void main(String[] a) {
        if ("-s".equals(a[0]))// -s udp监听端口 tcpHost tcPort
            server(Integer.valueOf(a[1]), a[2], Integer.valueOf(a[3]));
        else if ("-c".equals(a[0]))//-c tcp监听端口 udpServerHost udpServerPort
            client(Integer.valueOf(a[1]), a[2], Integer.valueOf(a[3]));
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
        Channel udpServer = Netty.udp(port, new UdpTcpTransfer(new InetSocketAddress(host, port), tcpMap)).channel();
        Netty.accept(tcpListenPort, new ChannelInitializer<Channel>() {
            @Override
            protected void initChannel(Channel ch) throws Exception {
                logger.info("accept {}", ch.remoteAddress());
                ch.pipeline().addLast(new TcpUdpTransfer(tcpMap, udpServer));
            }
        }).channel().closeFuture().syncUninterruptibly();
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
        udpServer = Netty.udp(udpListenPort, new UdpTcpTransfer(null, tcpMap) {
            @Override
            protected void write2Tcp(ByteBuf buf) {
                tcpMap.computeIfAbsent(buf.readInt(), integer -> {
                    TcpUdpTransfer tcpUdpTransfer = new TcpUdpTransfer(tcpMap, udpServer);
                    ChannelFuture future = Netty.connect(host, port, tcpUdpTransfer);
                    future.addListener(future1 -> {
                        if (!future1.isSuccess()) {
                            logger.warn("connect fail,tcpUdpTransfer.close");
                            tcpUdpTransfer.close();
                        }
                    });
                    return future.channel();
                }).writeAndFlush(buf);
            }
        }).channel();
        udpServer.closeFuture().syncUninterruptibly();
    }

    private static Channel udpServer;
}
