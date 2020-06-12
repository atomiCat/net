package org.jd.net.core.rudp;

import io.netty.channel.*;
import org.jd.net.core.CloseOnException;
import org.jd.net.core.Netty;
import org.jd.net.core.Transfer;

import java.net.InetSocketAddress;

/**
 * 将 tcp 数据通过 udp协议 发送到 remote
 * 并将从 remote 接收到的数据返回给 tcp
 * 需要与 {@link #stopAutoRead(Channel)} 配合使用
 *
 * @see #stopAutoRead(Channel)
 */
public class DuplexTransferRUDP extends ChannelInboundHandlerAdapter {
    private ChannelHandlerContext tcp;

    @Override
    public void handlerAdded(ChannelHandlerContext ctx) throws Exception {
        tcp = ctx;
        super.handlerAdded(ctx);
    }

    /**
     * @param udpAddr udp address
     */
    public DuplexTransferRUDP(InetSocketAddress udpAddr, ChannelHandler... hostPortHandler) {
        Netty.udp(0, new ChannelInboundHandlerAdapter() {
            @Override
            public void channelRegistered(ChannelHandlerContext udp) throws Exception {
                ChannelPipeline pipeline = udp.pipeline();
                pipeline.addLast(new RUDPHandler(udpAddr, tcp));
                if (hostPortHandler != null)
                    pipeline.addLast(hostPortHandler);

                pipeline.addLast(
                        CloseOnException.handler,
                        new ChannelInboundHandlerAdapter() {
                            @Override
                            public void channelActive(ChannelHandlerContext ctx) throws Exception {
                                tcp.pipeline().addLast(new Transfer(udp));//ctx数据复制到hostPort
                                tcp.channel().config().setAutoRead(true);
                                super.channelActive(ctx);
                            }
                        },
                        new Transfer(tcp)
                );
                udp.fireChannelRegistered();
            }
        }).addListener(future -> {
            if (!future.isSuccess())
                tcp.close();//连接失败，关闭
        });
    }

    /**
     * 停止channel自动读取
     *
     * @return this
     */
    public DuplexTransferRUDP stopAutoRead(Channel channel) {
        channel.config().setAutoRead(false);
        return this;
    }
}
