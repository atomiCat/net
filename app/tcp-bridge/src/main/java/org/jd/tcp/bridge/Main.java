package org.jd.tcp.bridge;

import io.netty.channel.Channel;
import org.jd.net.netty.Buf;
import org.jd.net.netty.Netty;
import org.jd.net.netty.handler.Transfer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Scanner;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * 服务器 S 在局域网中，通过 NAT 访问网络，无固定公网ip
 * 此时客户端 C 想访问 S ，必须有一个有公网IP的代理来转发数据，代理需要两部分实现：
 * 1.代理目的端 PT ：运行在 S 所在的局域网中，主动连接 PS 和 S ，并转发二者数据
 * 2.代理服务端 PS ：运行在有公网IP的机器上，等待 PT 和 C 的连接，并转发二者数据
 * 数据流向：C 》》PS 》》PT 》》S
 */
public class Main {
    static Logger logger = LoggerFactory.getLogger(Main.class);

    public static void main(String[] a) {
        if ("-s".equalsIgnoreCase(a[0])) {// -s clientPort targetPort
            serverStart(Integer.valueOf(a[1]), Integer.valueOf(a[2]));
        } else {// port serverHost serverPort password
//            targetStart(Integer.valueOf(a[0]), a[1], Integer.valueOf(a[2]), a[3]);
        }
    }

    public static final byte WAIT = 1, START = 2;//信号

    /**
     * 维护一个集合储存来自 PT 的连接
     * 等待 PT 和 C 连接
     * 1.当 PT 连接成功，将 PT连接 存入集合
     * 并添加Handler，如果 PT 发来的心跳包==WAIT，则丢弃
     * 一旦发现 PT 发来的心跳包变成 START，则不断将 PT 的数据发往 C
     * 2.当 C 连接成功，从集合中取出 PT连接 （如果没有PT连接直接断开C），不断将 C 的数据发往 PT
     *
     * @param targetPort 监听端口供 PT 连接
     * @param clientPort 监听端口供 客户端 连接
     */
    static void serverStart(int targetPort, int clientPort) {
        ConcurrentLinkedQueue<Channel> proxyTargets = new ConcurrentLinkedQueue<>();
        Netty.accept(targetPort, PT -> {
            proxyTargets.offer(PT);
            PT.pipeline().addLast(new ProxyServerHandler());
        });

        Netty.accept(targetPort, C -> {
            Channel PT = proxyTargets.poll();//当 C 连接成功，从集合中取出 任意一个PT连接
            if (PT == null || !PT.isActive()) {//检查PT可用性
                C.close();
                return;
            }

            PT.pipeline().get(ProxyServerHandler.class).setClient(C);

            PT.writeAndFlush(Buf.wrapByte(START));//通知 PT 发起连接
            C.pipeline().addLast(new Transfer(PT));//C 的数据转发给 PT
        });
    }

    static void targetStart(String sHost, int sPort, String password, String tHost, int tPort) throws InterruptedException {

        for (int i = 0; i < 3; i++) {
            new Thread(() -> {
                try {
                    while (true) {
                        Netty.connect(sHost, sPort, PS -> {
                            PS.pipeline().addLast(new KeepAliveHandler(5));
                        }).channel().closeFuture().sync();
                    }
                } catch (InterruptedException e) {
                    logger.warn("", e);
                }

            }).start();
        }
        Scanner scanner = new Scanner(System.in);
        while (true) {
            String s = scanner.nextLine();
            if ("stop".equals(s) || "exit".equals(s))
                System.exit(0);
        }
    }
}
