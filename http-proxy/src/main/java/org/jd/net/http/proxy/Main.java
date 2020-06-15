package org.jd.net.http.proxy;

import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import org.jd.net.core.ChannelEvent;
import org.jd.net.core.CloseOnIOException;
import org.jd.net.core.DuplexTransfer;
import org.jd.net.core.Netty;
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

    static void clientStart(int port, String sHost, int sPort, String password) {
        Netty.accept(port, new ChannelInitializer<Channel>() {
            @Override
            protected void initChannel(Channel ch) throws Exception {
                ch.pipeline().addLast(
                        new DuplexTransfer(
                                sHost, sPort, ChannelEvent.channelActive,
                                new XorCodec(password), CloseOnIOException.handler
                        ).stopAutoRead(ch),
                        CloseOnIOException.handler
                );
            }
        }).syncUninterruptibly().channel().closeFuture().syncUninterruptibly();

    }

    static void serverStart(int port, String password) {
        Netty.accept(port, new ChannelInitializer<Channel>() {
            @Override
            protected void initChannel(Channel ch) throws Exception {
                ch.pipeline().addLast(
                        new XorCodec(password),
                        new HttpProxyService()
                );
            }
        }).addListener(future -> {
            if (future.isSuccess())
                logger.info("serverStart success {}", port);
            else
                logger.error("serverStart fail", future.cause());
        }).syncUninterruptibly().channel().closeFuture().syncUninterruptibly();
    }

}
