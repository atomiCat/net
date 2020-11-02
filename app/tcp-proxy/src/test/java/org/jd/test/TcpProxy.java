package org.jd.test;

import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.util.ResourceLeakDetector;
import org.jd.net.core.Buf;
import org.jd.net.core.Handlers;
import org.jd.net.core.Netty;
import org.jd.net.tcp.proxy.Main;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class TcpProxy {
    Logger logger = LoggerFactory.getLogger(TcpProxy.class);

    @Test
    public void test() throws InterruptedException {
        ResourceLeakDetector.setLevel(ResourceLeakDetector.Level.PARANOID);//设置资源泄露检查等级：最高等级

        Main.start(1111, "127.0.0.1", 2222);
        Main.start(2222, "127.0.0.1", 3333);
        Main.start(3333, "127.0.0.1", 4444);
        Main.start(4444, "127.0.0.1", 5555);
        Main.start(5555, "127.0.0.1", 6666);
        Main.start(6666, "127.0.0.1", 7777);
        Main.start(7777, "127.0.0.1", 8888);
        Main.start(8888, "127.0.0.1", 9999);

        Netty.accept(9999, channel -> channel.pipeline().addLast(Handlers.read((ch, buf) -> {
            int l = buf.readInt();
            buf.writeInt(l + 1);
            ch.writeAndFlush(buf);

            if (l > 1000) {
                logger.info("count={} ", l);
                ch.close();
            }
        })));

        Netty.connect("127.0.0.1", 1111, channel -> channel.pipeline().addLast(Handlers.active(context -> {
            logger.info("active ");
            context.writeAndFlush(Buf.wrap(1));
        }), Handlers.read((ch, buf) -> {
            int l = buf.readInt();
            buf.writeInt(l + 1);
            ch.writeAndFlush(buf);


        }))).channel().closeFuture().sync();
        logger.info("end ");
    }
}
