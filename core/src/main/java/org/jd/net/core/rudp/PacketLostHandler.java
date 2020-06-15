package org.jd.net.core.rudp;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.CompositeByteBuf;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.channel.socket.DatagramPacket;
import org.jd.net.core.Buf;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;
import java.util.LinkedList;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 丢包重发,保证不丢包
 * 接收到重复的包去重
 */
public class PacketLostHandler extends ChannelDuplexHandler {
    static Logger logger = LoggerFactory.getLogger(PacketLostHandler.class);
    static final int responseFlag = -556315684;//必须小于0

    static class Datagram {
        private final DatagramPacket packet;
        long[] sendTime = new long[10];
        int sendTimeLength = 0;
        long respTime;

        public Datagram(DatagramPacket packet) {
            this.packet = packet;
        }

        /**
         * 复制一个 DatagramPacket 用于发送
         */
        public DatagramPacket packet() {
            sendTime[sendTimeLength++] = System.currentTimeMillis();
            return packet.replace(packet.content().retainedSlice());
        }
    }

    ConcurrentHashMap<Integer, Datagram> datagrams = new ConcurrentHashMap<>();//已发送的数据包
    AtomicInteger index = new AtomicInteger(0);

    @Override
    public void write(ChannelHandlerContext udp, Object msg, ChannelPromise promise) throws Exception {
        DatagramPacket packet = (DatagramPacket) msg;
        int packetIndex = index.getAndAdd(1);
        CompositeByteBuf bufs = Buf.alloc.compositeBuffer(2)
                .addComponents(true, Buf.wrap(packetIndex), packet.content());
        Datagram datagram = new Datagram(packet.replace(bufs));
        datagrams.put(packetIndex, datagram);
        udp.write(datagram.packet());
        super.write(udp, msg, promise);
    }

    private Thread resendThread;

    @Override
    public void channelActive(ChannelHandlerContext udp) throws Exception {
        resendThread = new Thread(() -> {
            try {
                while (true) {
                    TimeUnit.MILLISECONDS.sleep(150);
                    int resentCount = 0, datagramSize = datagrams.size();
                    for (Integer i : datagrams.keySet()) {
                        Datagram datagram = datagrams.get(i);
                        if (datagram.respTime == 0) {//没有响应
                            udp.channel().write(datagram.packet());
                            resentCount++;
                        } else {//已响应
                            datagram.packet.content().release();
                            datagram.packet.release();
                            datagrams.remove(i);
                        }
                    }
                    if (resentCount > 0) {
                        udp.channel().flush();
                        logger.info("丢包重发：{}/{} ", resentCount, datagramSize);
                    }
                }
            } catch (InterruptedException e) {
            }
        });
        resendThread.start();
        super.channelActive(udp);
    }

    private LinkedList<ByteBuf> readBufs = new LinkedList<>();
    private HashSet<Integer> consumedIndex = new HashSet<>();//已经消费掉的

    @Override
    public void channelRead(ChannelHandlerContext udp, Object msg) throws Exception {
        DatagramPacket packet = (DatagramPacket) msg;
        ByteBuf buf = packet.content();
        int index = buf.readInt();
        if (index == responseFlag) {//收到对方的响应
            Datagram datagram = datagrams.get(buf.readInt());
            if (datagram.respTime == 0)//只记录第一次收到响应的时间
                datagram.respTime = System.currentTimeMillis();
            packet.release();
            return;
        }
        udp.channel().writeAndFlush(Buf.wrap(responseFlag, index));//发送响应，告诉另一端的udp已经接收到包

        if (consumedIndex.contains(index)) {//收到重复包
            packet.release();
            return;
        }
        consumedIndex.add(index);//记录已经收到该包
        udp.fireChannelRead(msg);
    }

}
