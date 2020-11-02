package org.jd.net.tcp.proxy;

import io.netty.channel.ChannelFuture;
import org.jd.net.core.Handlers;
import org.jd.net.core.Netty;
import org.jd.net.core.Transfer;
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

            String[] s = line.split(" ");
            if (s.length == 3)
                start(Integer.valueOf(s[0]), s[1], Integer.valueOf(s[2]));
        });
        reader.close();
    }

    /**
     * @param listenPort 本机监听端口
     * @param host       被代理host
     * @param port       被代理port
     * @return
     */
    public static ChannelFuture start(int listenPort, String host, int port) {
        logger.info("启动tcp代理：{} --> {}:{}", listenPort, host, port);
        return Netty.accept(listenPort, client -> {
            logger.info("accepted({}): {}", listenPort, client.remoteAddress());
            client.config().setAutoRead(false);//暂停自动读，等连接被代理端成功再继续读
            Netty.connect(host, port, target -> {
                target.pipeline().addLast(new Transfer(client), Handlers.closeOnIOException);
                client.pipeline().addLast(new Transfer(target), Handlers.closeOnIOException);
                client.config().setAutoRead(true);
            });
        });
    }
}
