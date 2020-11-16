package org.jd.tcp.bridge;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import org.jd.net.netty.Buf;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeUnit;

/**
 * TCP空闲时发送心跳包，保证TCP存活
 */
public class KeepAliveHandler extends ChannelDuplexHandler {
    private static Logger logger = LoggerFactory.getLogger(KeepAliveHandler.class);
    private final int interval;

    /**
     * @param interval 两次心跳包之间的时间间隔
     */
    public KeepAliveHandler(int interval) {
        this.interval = interval;
    }

    private int retain;

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        ByteBuf buf = (ByteBuf) msg;
        while (retain == 0 && buf.isReadable()) {//可能多个心跳包一起收到,所以循环读
            retain = buf.readInt();
            logger.info("读到心跳包 {}", retain);
        }
        if (!buf.isReadable()) {
            buf.release();
        } else if (buf.readableBytes() <= retain) {
            retain -= buf.readableBytes();
            ctx.fireChannelRead(buf);
        } else {
            ctx.fireChannelRead(buf.readRetainedSlice(retain));//retain 字节之内的数据交给下一个handler处理
            retain = 0;
            this.channelRead(ctx, buf);//剩余数据继续递归处理
        }
    }

    private boolean active = true;

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        this.ctx = ctx;
        new Thread(() -> {
            while (active) {
                try {
                    TimeUnit.SECONDS.sleep(interval);
                    write(Buf.alloc(0), null);
                    this.ctx.flush();
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }

        }).start();
        super.channelActive(ctx);
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        active = false;
        super.channelInactive(ctx);
    }

    private ChannelHandlerContext ctx;

    /**
     * 写数据之前，先写入一个大小等于 数据长度 的 int值
     */
    @Override
    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) {
        this.write((ByteBuf) msg, promise);
    }


    private synchronized void write(ByteBuf data, ChannelPromise promise) {
        ctx.write(Buf.wrap(data.readableBytes())).syncUninterruptibly();//写入一个大小等于 数据长度 的 int值
        if (data.isReadable()) {
            if (promise == null)
                ctx.write(data).syncUninterruptibly();
            else
                ctx.write(data, promise).syncUninterruptibly();
        }
    }
}
