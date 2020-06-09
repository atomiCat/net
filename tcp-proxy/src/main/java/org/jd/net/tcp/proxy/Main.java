package org.jd.net.tcp.proxy;

import io.netty.channel.*;
import org.jd.net.core.DuplexTransfer;
import org.jd.net.core.Netty;
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
        logger.info("配置文件：{}", file.getAbsolutePath());
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

    private static ChannelFuture start(int listenPort, String host, int port) {
        logger.info("启动tcp代理：{} --> {}:{}", listenPort, host, port);
        return Netty.accept(listenPort, new ChannelInitializer<Channel>() {
            @Override
            protected void initChannel(Channel ch) throws Exception {
                logger.info("accepted {} = {}", listenPort, ch.remoteAddress());
                ch.pipeline().addLast(new DuplexTransfer(host, port).stopAutoRead(ch));
            }
        });
    }
}
