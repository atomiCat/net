package org.jd.net.http.proxy;

import io.netty.channel.Channel;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelOption;
import org.jd.net.netty.Netty;
import org.jd.net.netty.handler.Handlers;
import org.jd.net.netty.handler.Transfer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.Supplier;

public class Main {
    private static Logger logger = LoggerFactory.getLogger(Main.class);

    /**
     * 启动server端：-s port [password]
     * 启动client端：port serverHost serverPort [password]
     */
    public static void main(String[] a) {
        if ("-s".equalsIgnoreCase(a[0])) {// -s port [password]
            serverStart(Integer.valueOf(a[1]), a.length > 2 ? () -> new AESCodec(a[2]) : null);
        } else {// port serverHost serverPort [password]
            clientStart(Integer.valueOf(a[0]), a[1], Integer.valueOf(a[2]), a.length > 3 ? () -> new AESCodec(a[3]) : null);
        }
    }

    /**
     * 启动客户端
     *
     * @param port          监听port
     * @param sHost         服务端host
     * @param sPort         服务端port
     * @param codecSupplier 编解码器工厂
     */
    public static void clientStart(int port, String sHost, int sPort, Supplier<ChannelDuplexHandler> codecSupplier) {
        Channel channel = Netty.accept(port, client -> {
            client.config().setAutoRead(false);//暂停自动读，等连接到代理服务器再继续
            Netty.connect(sHost, sPort, proxy -> {
                if (codecSupplier != null)//添加编解码器
                    proxy.pipeline().addLast(codecSupplier.get());

                proxy.pipeline().addLast(Transfer.autoReadOnActive(client), Handlers.closeOnIOException);
                client.pipeline().addLast(new Transfer(proxy), Handlers.closeOnIOException);
            });
        }).channel();
        channel.config().setOption(ChannelOption.TCP_NODELAY, true);
        channel.config().setOption(ChannelOption.SO_SNDBUF, 1024 * 512);
        channel.closeFuture().syncUninterruptibly();

    }

    /**
     * @param port          监听端口
     * @param codecSupplier 编解码器工厂
     */
    public static void serverStart(int port, Supplier<ChannelDuplexHandler> codecSupplier) {
        Channel channel = Netty.accept(port, ch -> {
            if (codecSupplier != null)
                ch.pipeline().addLast(codecSupplier.get());
            ch.pipeline().addLast(new HttpProxyService());
        }).addListener(future -> {
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
