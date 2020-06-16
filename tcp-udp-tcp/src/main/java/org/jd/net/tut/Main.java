package org.jd.net.tut;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.CompositeByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.channel.socket.DatagramPacket;
import org.jd.net.core.Buf;
import org.jd.net.core.Netty;

import java.net.InetSocketAddress;
import java.util.HashSet;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class Main {
    public static void main(String[] a) {

    }

    /**
     * 启动客户端
     *
     * @param tcpListenPort 客户端tcp监听端口
     * @param host          服务端
     * @param port          服务端
     */
    static void client(int tcpListenPort, String host, int port) {
        ConcurrentHashMap<Integer, Channel> tcpMap = new ConcurrentHashMap<>();
        Channel udpServer = Netty.udp(port, new ChannelDuplexHandler() {
            @Override
            public void handlerAdded(ChannelHandlerContext ctx) throws Exception {
                ctx.pipeline().addFirst(new PacketLostHandler());
                super.handlerAdded(ctx);
            }

            @Override
            public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
                ByteBuf buf = ((DatagramPacket) msg).content();
                Channel tcp = tcpMap.get(buf.readInt());//根据channelIndex选择合适的tcp连接
                if (tcp != null) {
                    tcp.writeAndFlush(buf);
                } else {
                    buf.release();
                }
            }
        }).syncUninterruptibly().channel();

        AtomicInteger channelIndexFactor = new AtomicInteger(0);
        Netty.accept(tcpListenPort, new ChannelInitializer<Channel>() {
            @Override
            protected void initChannel(Channel ch) throws Exception {
                int channelIndex = channelIndexFactor.getAndAdd(1);
                tcpMap.put(channelIndex, ch);
                AtomicInteger dataIndexFactor = new AtomicInteger(0);
                ch.pipeline().addLast(new ChannelDuplexHandler() {
                    @Override//添加channelIndex 和 dataIndex
                    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
                        CompositeByteBuf buf = Unpooled.compositeBuffer(2).
                                addComponents(true,
                                        Buf.wrap(channelIndex, dataIndexFactor.getAndAdd(1)),
                                        (ByteBuf) msg
                                );
                        udpServer.writeAndFlush(new DatagramPacket(buf, new InetSocketAddress(host, port)));
                    }

                    TreeSet<IndexBuf> indexBufs = new TreeSet<>();

                    @Override//将udp写来的包排序
                    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
                        ByteBuf buf = (ByteBuf) msg;
                        indexBufs.add(new IndexBuf(buf.readInt(), buf));
                        HashSet<IndexBuf> toRemove = new HashSet<>();
                        IndexBuf that = null;
                        for (IndexBuf next : indexBufs) {
                            if (that != null) {
                                if (that.index + 1 == next.index) {
                                    ctx.write(that.byteBuf, promise);
                                    toRemove.add(that);
                                } else {
                                    break;
                                }
                            }
                            that = next;
                            if (that.byteBuf.readableBytes() == 0) {//最后一个数据包
                                that.byteBuf.release();
                            }
                        }
                        indexBufs.removeAll(toRemove);//移除已消费
                    }

                    @Override
                    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
                        tcpMap.remove(channelIndex);
                        super.channelInactive(ctx);
                    }
                });
            }
        }).syncUninterruptibly().channel().closeFuture().syncUninterruptibly();
    }

    /**
     * 启动服务端
     *
     * @param udpListenPort 服务端udp监听端口
     * @param host
     * @param port
     */
    static void server(int udpListenPort, String host, int port) {

    }
}
