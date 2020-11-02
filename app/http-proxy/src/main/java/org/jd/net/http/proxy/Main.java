package org.jd.net.http.proxy;

import io.netty.channel.Channel;
import io.netty.channel.ChannelOption;
import org.jd.net.core.Handlers;
import org.jd.net.core.Netty;
import org.jd.net.core.Transfer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Main {
    static Logger logger = LoggerFactory.getLogger(Main.class);

    public static void main(String[] a) {
        if ("-s".equalsIgnoreCase(a[0])) {// -s port password
            serverStart(Integer.valueOf(a[1]), a[2]);
        } else {// port serverHost serverPort password
            clientStart(Integer.valueOf(a[0]), a[1], Integer.valueOf(a[2]), a[3]);
        }
    }

    public static void clientStart(int port, String sHost, int sPort, String password) {
        Channel channel = Netty.accept(port, client -> {
            client.config().setAutoRead(false);//暂停自动读，等连接到代理服务器再继续
            Netty.connect(sHost, sPort, proxy -> {
                proxy.pipeline().addLast(new XorCodec(password), new Transfer(client), Handlers.closeOnIOException);
                client.pipeline().addLast(new Transfer(proxy), Handlers.closeOnIOException);
                client.config().setAutoRead(true);
            });
        }).channel();
        channel.config().setOption(ChannelOption.TCP_NODELAY, true);
        channel.config().setOption(ChannelOption.SO_SNDBUF, 1024 * 512);
        channel.closeFuture().syncUninterruptibly();

    }

    public static void serverStart(int port, String password) {
        Channel channel = Netty.accept(port, ch -> ch.pipeline().addLast(new XorCodec(password), new HttpProxyService()))
                .addListener(future -> {
                    if (future.isSuccess())
                        logger.info("serverStart success {}", port);
                    else
                        logger.error("serverStart fail", future.cause());
                }).channel();
        channel.config().setOption(ChannelOption.TCP_NODELAY, true);
        channel.config().setOption(ChannelOption.SO_SNDBUF, 1024 * 512);
        channel.closeFuture().syncUninterruptibly();
    }

}
