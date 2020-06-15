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
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 丢包重发,保证不丢包
 * 接收到重复的包去重
 */
public class PacketLostHandler extends ChannelDuplexHandler {
    static Logger logger = LoggerFactory.getLogger(PacketLostHandler.class);
    static final int responseFlag = -556315684;//必须小于0

    static class DataInfo {
        private final DatagramPacket packet;
        public final int index;
        long sendTime;//发送时间
        int sendTimes;//发送次数
        long respTime;//响应时间

        public DataInfo(DatagramPacket packet) {
            this.packet = packet;
            index = packet.content().getInt(packet.content().readerIndex());
        }

        /**
         * 复制一个 DatagramPacket 用于发送
         */
        public DatagramPacket packet() {
            sendTime = System.currentTimeMillis();
            sendTimes++;
            return packet.replace(packet.content().retainedSlice());
        }
    }

    ConcurrentLinkedQueue<DataInfo> dataQueue = new ConcurrentLinkedQueue<>();//已发送的数据包
    ConcurrentHashMap<Integer, DataInfo> dataMap = new ConcurrentHashMap<>();//已发送的数据包
    AtomicInteger index = new AtomicInteger(0);

    @Override
    public void write(ChannelHandlerContext udp, Object msg, ChannelPromise promise) throws Exception {
        DatagramPacket packet = (DatagramPacket) msg;
        CompositeByteBuf bufs = Buf.alloc.compositeBuffer(2)
                .addComponents(true, Buf.wrap(index.getAndAdd(1)), packet.content());
        DataInfo dataInfo = new DataInfo(packet.replace(bufs));
        dataQueue.offer(dataInfo);
        dataMap.put(dataInfo.index, dataInfo);
        udp.write(dataInfo.packet());
        super.write(udp, msg, promise);
    }

    private Thread resendThread;

    @Override
    public void channelActive(ChannelHandlerContext udp) throws Exception {
        resendThread = new Thread(() -> {
            try {
                while (true) {
                    TimeUnit.MILLISECONDS.sleep(10);
                    int sent = 0, count = dataMap.size();
                    for (DataInfo dataInfo = dataQueue.peek(); dataInfo != null && System.currentTimeMillis() - dataInfo.sendTime > 200; dataInfo = dataQueue.peek()) {
                        dataQueue.poll();//移除头部
                        if (dataInfo.respTime == 0) {//没有响应
                            udp.channel().write(dataInfo.packet());//重发
                            dataQueue.offer(dataInfo);//放到队列尾部
                            sent++;
                        } else {//已响应
                            dataInfo.packet.release();
                            dataMap.remove(dataInfo.index);
                        }
                    }
                    if (sent > 0) {
                        udp.channel().flush();
                        logger.info("丢包重发：{}/{} ", sent, count);
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
        int index = packet.content().readInt();

        if (index == responseFlag) {//收到对方的响应
            DataInfo dataInfo = dataMap.get(index);
            if (dataInfo.respTime == 0)//只记录第一次收到响应的时间
                dataInfo.respTime = System.currentTimeMillis();
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
