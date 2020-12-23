package org.jd.test;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.util.ResourceLeakDetector;
import org.jd.net.netty.Buf;
import org.jd.net.netty.Netty;
import org.jd.net.netty.handler.Handlers;
import org.jd.net.tcp.proxy.Main;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeUnit;


public class TcpProxy {
    static Logger logger = LoggerFactory.getLogger(TcpProxy.class);

    @Test
    public void start() throws InterruptedException {
        Main.start("0.0.0.0", 8000, "127.0.0.1", 50586);
        TimeUnit.DAYS.sleep(1);
    }

    @Test
    public void test() throws InterruptedException {
        ResourceLeakDetector.setLevel(ResourceLeakDetector.Level.PARANOID);//设置资源泄露检查等级：最高等级

        Main.start("0.0.0.0", 1111, "127.0.0.1", 2222);


        Netty.accept(2222, channel -> channel.pipeline().addLast(Handlers.active(context -> {
            logger.info("active ");
            ByteBuf buf = Buf.alloc(1024);
            buf.writeInt(1);
            //用负数填充
            while (buf.isWritable())
                buf.writeByte(-1);
            context.writeAndFlush(buf);
        }), new TestHandler()));

        Netty.connect("127.0.0.1", 1111, channel -> channel.pipeline().addLast(new TestHandler()))
                .channel().closeFuture().sync();
        logger.info("end");
    }

    /**
     * 读到数据时，将第一个int数字自增1后，剩余数据原封不动写回
     */
    static class TestHandler extends ChannelInboundHandlerAdapter {
        long startTime = System.currentTimeMillis();

        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
            ByteBuf buf = (ByteBuf) msg;
            //第一个数自增1
            int num = buf.getInt(0);
            if (num < 0)
                throw new IllegalStateException("buf.readableBytes = " + buf.readableBytes());
            buf.setInt(0, num + 1);

            if (num >= 30000) {//停止条件
                long byteLen = buf.readableBytes();
                long cost = System.currentTimeMillis() - startTime;
                logger.info("IO读写次数{} 单次传输{}字节 耗时{}ms", num, byteLen, cost);
                logger.info("传输总数据量{}字节, 速度{}字节每秒", byteLen * num, byteLen * num * 1000 / cost);
                ctx.close();
                buf.release();
                return;
            }

            ctx.writeAndFlush(buf);
        }
    }
}
