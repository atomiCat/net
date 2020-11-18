package org.jd.net.tcp.proxy;

import io.netty.channel.ChannelFuture;
import org.jd.net.netty.Netty;
import org.jd.net.netty.handler.Handlers;
import org.jd.net.netty.handler.Transfer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.net.URL;

public class Main {
    static Logger logger = LoggerFactory.getLogger(Main.class);

    /**
     * 启动添加 -Dio.netty.leakDetectionLevel=paranoid 监控有无内存泄漏
     * -Dio.netty.leakDetectionLevel=disabled 禁用监控
     */
    public static void main(String[] a) throws IOException {
        File file = new File(a.length > 0 ? a[0] : "tcp-proxy.conf");
        if (!file.isFile()) {
            //开发环境
            URL url = Main.class.getClassLoader().getResource("tcp-proxy.conf");
            if (url != null)
                file = new File(url.getFile());

            if (!file.isFile()) {
                logger.info("未找到配置文件");
                return;
            }
        }
        logger.info("read config from : {}", file.getAbsolutePath());
        BufferedReader reader = new BufferedReader(new FileReader(file));
        reader.lines().forEach(line -> {
            if (line.length() == 0)
                return;
            char c = line.charAt(0);
            if (c < '0' || c > '9')
                return;

            String[] p = line.split(" ");
            if (p.length == 3)
                start("0.0.0.0", Integer.valueOf(p[0]), p[1], Integer.valueOf(p[2]));
            else if (p.length == 4)
                start(p[0], Integer.valueOf(p[1]), p[2], Integer.valueOf(p[3]));
        });
        reader.close();
    }

    /**
     * @param listenHost 本机监听host
     * @param listenPort 本机监听port
     * @param host       被代理host
     * @param port       被代理port
     * @return
     */
    public static ChannelFuture start(String listenHost, int listenPort, String host, int port) {
        logger.info("启动tcp代理：{}:{} --> {}:{}", listenHost, listenPort, host, port);
        return Netty.accept(listenHost, listenPort, client -> {
            logger.info("accepted({}): {}",client.localAddress(), client.remoteAddress());
            client.config().setAutoRead(false);//暂停自动读，等连接被代理端成功再继续读
            Netty.connect(host, port, target -> {
                target.pipeline().addLast(Transfer.autoReadOnActive(client), Handlers.closeOnIOException);
                client.pipeline().addLast(new Transfer(target), Handlers.closeOnIOException);
            }).addListener(future -> {
                if (!future.isSuccess()) {
                    logger.info("连接失败，关闭 client {} 的连接", client.remoteAddress());
                    client.close();
                }
            });
        });
    }
}
