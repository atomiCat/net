package org.jd.net.core.rudp;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.CompositeByteBuf;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelPromise;
import io.netty.channel.socket.DatagramPacket;
import org.jd.net.core.Buf;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 将tcp数据转为udp数据发送到远端
 * 将读到的udp报文转为tcp并触发channelRead
 */
public class RUDPHandler extends ChannelDuplexHandler {
    static Logger logger = LoggerFactory.getLogger(RUDPHandler.class);
    static ExecutorService executorService = Executors.newFixedThreadPool(20);

    public interface FLAG {
        int response = -556315684;//<0
        int data = 15168546;
        int close = 95215684;
    }

    private final InetSocketAddress udpAddr;
    private final ChannelHandlerContext tcp;

    public RUDPHandler(InetSocketAddress udpAddr, ChannelHandlerContext tcp) {
        this.udpAddr = udpAddr;
        this.tcp = tcp;
    }

    @Override
    public void handlerAdded(ChannelHandlerContext udp) throws Exception {
        tcp.pipeline().addLast(new ChannelInboundHandlerAdapter() {
            @Override
            public void channelInactive(ChannelHandlerContext ctx) throws Exception {
                udp.close();
                super.channelInactive(ctx);
            }
        });
        super.handlerAdded(udp);
    }

    @Override
    public void channelInactive(ChannelHandlerContext udp) throws Exception {
        udp.channel().writeAndFlush(Buf.wrap(FLAG.close));
        tcp.close();
        resend.cancel(true);
        super.channelInactive(udp);
    }

    static class Sudp {
        ByteBuf buf;
        long[] sendTime = new long[10];
        int sendTimeLength = 0;
        long respTime;

        public Sudp setBuf(ByteBuf buf) {
            this.buf = buf;
            sendTime[sendTimeLength++] = System.currentTimeMillis();
            return this;
        }
    }

    private ArrayList<Sudp> writeSudp = new ArrayList<>();//已发送

    AtomicInteger index = new AtomicInteger(0);

    @Override
    public void write(ChannelHandlerContext udp, Object msg, ChannelPromise promise) throws Exception {
        CompositeByteBuf bufs = Buf.alloc.compositeBuffer(2)
                .addComponents(true, Buf.wrap(index.getAndAdd(1)), (ByteBuf) msg);
        writeSudp.add(new Sudp().setBuf(bufs.retainedSlice()));
        udp.write(new DatagramPacket(bufs, udpAddr));
        super.write(udp, msg, promise);
    }

    private Future<?> resend;

    @Override
    public void channelActive(ChannelHandlerContext udp) throws Exception {
        resend = executorService.submit(() -> {
            try {
                while (true) {
                    TimeUnit.MILLISECONDS.sleep(150);
                    int resentCount = 0;
                    for (int i = 0; i < writeSudp.size(); i++) {
                        Sudp sudp = writeSudp.get(i);
                        if (sudp.respTime == 0) {//没有响应
                            ByteBuf newBuf = sudp.buf.retainedSlice();
                            udp.channel().writeAndFlush(sudp.buf);
                            sudp.setBuf(newBuf);
                            resentCount++;
                        } else if (sudp.buf != null) {
                            sudp.buf.release();
                            sudp.buf = null;
                        }
                    }
                    if (resentCount > 0) {
                        logger.info("丢包重发：{}/{} ", resentCount, writeSudp.size());
                    }
                }
            } catch (InterruptedException e) {
            }
        });

        super.channelActive(udp);
    }

    private ArrayList<ByteBuf> readBufs = new ArrayList<>();
    private int consumed = 0;

    @Override
    public void channelRead(ChannelHandlerContext udp, Object msg) throws Exception {
        DatagramPacket packet = (DatagramPacket) msg;
        ByteBuf buf = packet.content();
        int index = buf.readInt();
        if (index == FLAG.response) {//收到对方的响应
            writeSudp.get(buf.readInt()).respTime = System.currentTimeMillis();
            buf.release();
            return;
        } else {//告诉另一端的udp已经接收到包
            udp.channel().writeAndFlush(Buf.wrap(FLAG.response, index));
        }

        if (index < consumed) {//收到重复包
            buf.release();
            return;
        }
        while (index >= readBufs.size()) {
            readBufs.add(null);
        }
        readBufs.add(index, buf);

        for (int i = consumed; i < readBufs.size(); i++) {
            ByteBuf byteBuf = readBufs.get(i);
            if (byteBuf == null)
                break;
            switch (byteBuf.readInt()) {
                case FLAG.data:
                    udp.fireChannelRead(byteBuf);
                    break;
                case FLAG.close:
                    udp.close();
                    byteBuf.release();
                    break;
                default:
                    throw new IllegalArgumentException("标志位有误！");
            }
        }

//        super.channelRead(udp, msg);//不需要继续传递
    }

}
