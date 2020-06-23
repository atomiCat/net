package org.jd.net.tut;

import io.netty.buffer.CompositeByteBuf;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.channel.socket.DatagramPacket;
import org.jd.net.core.Buf;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * udp
 * 丢包重发,保证不丢包
 * 接收到重复的包去重
 */
public class PacketLostHandler extends ChannelDuplexHandler {
    private static Logger logger = LoggerFactory.getLogger(PacketLostHandler.class);
    private static final int responseFlag = -556315684;//必须小于0

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
         * 并更新 sendTime
         */
        public DatagramPacket packet() {
            if (sendTimes > 100) {
                throw new IllegalStateException("重发次数超过50");
            }
            this.sendTime = System.currentTimeMillis();
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
    }

    private Thread resendThread;
    private long netDelay = 200;

    @Override
    public void channelActive(ChannelHandlerContext udp) throws Exception {
        resendThread = new Thread(() -> {
            try {
                while (true) {
                    List<DataInfo> tail = new ArrayList<>();//丢包重发后放到队列尾部
                    int sent = 0, count = 0;
                    for (; !dataQueue.isEmpty(); count++) {
                        DataInfo dataInfo = dataQueue.poll();//移除头部
                        long sleep = netDelay - (System.currentTimeMillis() - dataInfo.sendTime);
                        if (sleep > 0) {
                            udp.flush();
                            TimeUnit.MILLISECONDS.sleep(sleep);
                        }

                        if (dataInfo.respTime == 0) {//没有响应
//                            logger.info("重发 {}", dataInfo.index);
                            udp.write(dataInfo.packet());//重发
                            tail.add(dataInfo);//放到队列尾部
                            sent++;
                        } else {//已响应
//                            logger.info("已响应,dataMap.remove {}", dataInfo.index);
                            dataInfo.packet.release();
                            dataMap.remove(dataInfo.index);
                        }
                    }
                    if (sent > 0) {
                        tail.forEach(dataQueue::offer);
                        udp.flush();
                        logger.info("丢包重发：{}/{} ", sent, count);
                    }

                }
            } catch (InterruptedException e) {
            }
        });
        resendThread.start();
        super.channelActive(udp);
    }

    private TreeSet<Integer> consumedIndex = new TreeSet<>();//已经消费掉的
    private Integer consumedIndexMin = -1;//index小于等于此值的包会被忽略

    @Override
    public void channelRead(ChannelHandlerContext udp, Object msg) throws Exception {
        DatagramPacket packet = (DatagramPacket) msg;
        int index = packet.content().readInt();//dataIndex

        if (index == responseFlag) {//收到对方的响应
            int realIndex = packet.content().readInt();
//            logger.info("收到响应 {}", realIndex);
            DataInfo dataInfo = dataMap.get(realIndex);
            //同一 dataIndex 可能收到多次响应
            if (dataInfo != null && dataInfo.respTime == 0)//只记录第一次收到响应的时间
                dataInfo.respTime = System.currentTimeMillis();
            packet.release();
            return;
        } else {
//            logger.info("收到udp包 index={}", index);
        }

        udp.writeAndFlush(new DatagramPacket(Buf.wrap(responseFlag, index), packet.sender()));//发送响应，告诉另一端的udp已经接收到包

        if (index <= consumedIndexMin || consumedIndex.contains(index)) {//收到重复包
            logger.info("收到重复包：index={}", index);
            packet.release();
            return;
        }
        consumedIndex.add(index);//记录已经收到该包
        udp.fireChannelRead(msg);

        Collection<Integer> toRemove = new HashSet<>();//待删除
        Integer that = null;
        for (Integer next : consumedIndex) {
            if (that != null) {
                if (next == that + 1) {//删除连续的已消费的index
                    toRemove.add(that);
                    consumedIndexMin = that;
                } else {
                    break;
                }
            }
            that = next;
        }
        consumedIndex.removeAll(toRemove);
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        resendThread.interrupt();
        super.channelInactive(ctx);
    }
}
