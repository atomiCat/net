package org.jd.net.http.proxy;

import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelOption;
import org.jd.net.netty.Buf;
import org.jd.net.netty.Netty;
import org.jd.net.netty.handler.Handlers;
import org.jd.net.netty.handler.Transfer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.function.Supplier;

public class Main {
    private static Logger logger = LoggerFactory.getLogger(Main.class);
    private static byte[] password = null;

    /**
     * 启动server端：
     * 启动client端：
     *
     */
    public static void main(String[] a) {
        boolean isServer = "-s".equalsIgnoreCase(a[0]);
        if ((isServer && a.length > 2))
            password = genPassword(a[2]);
        if (((!isServer) && a.length == 4))
            password = genPassword(a[3]);
        Supplier<ChannelDuplexHandler> codecSupplier = password == null ? null : () -> new XorCodec(password);

        if (isServer) {// -s port [password]
            serverStart(Integer.valueOf(a[1]), codecSupplier);
        } else {// port serverHost serverPort password
            clientStart(Integer.valueOf(a[0]), a[1], Integer.valueOf(a[2]), null);
        }
    }

    /**
     * 启动客户端
     * @param port 监听port
     * @param sHost 服务端host
     * @param sPort 服务端port
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
     *
     * @param port 监听端口
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

    /**
     * 通过明文密钥不断MD5生成一个长字节数组用来异或加解密
     */
    public static byte[] genPassword(String password) {
        ByteBuf passwordBytes = Buf.alloc.heapBuffer(2048);//通过MD5算法生成2048字节长度的密钥用来异或
        try {
            MessageDigest digest = MessageDigest.getInstance("MD5");
            byte[] bytes = password.getBytes(StandardCharsets.UTF_8);
            for (int i = 0; passwordBytes.isWritable(); i++) {
                bytes = digest.digest(bytes);
                if (i > 50)//从第50次MD5开始算有效密钥
                    passwordBytes.writeBytes(bytes, 0, Math.min(bytes.length, passwordBytes.writableBytes()));
            }
            return passwordBytes.array();//上面分配的是heapBuffer，所以此处不会抛异常
        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            passwordBytes.release();
        }
    }
}
