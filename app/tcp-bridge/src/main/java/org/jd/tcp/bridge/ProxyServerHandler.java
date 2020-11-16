package org.jd.tcp.bridge;

import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import org.jd.net.netty.handler.Transfer;

import java.util.Objects;

/**
 * 代理服务端 Handler
 */
public class ProxyServerHandler extends ChannelInboundHandlerAdapter {
    private byte state = Main.WAIT;//有且只有一次从 WAIT 变成 START
    private Channel client;

    /**
     * 必须在客户端连接成功后调用此方法设置客户端 Channel
     */
    public synchronized void setClient(Channel client) {
        if (this.client != null)
            throw new IllegalStateException("client has already been set!");
        this.client = client;
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        ByteBuf buf = (ByteBuf) msg;
        //正常情况 state 有且只有一次从 WAIT 变成 START，且跳变时会移除this，所以此时 state 只能是 WAIT
        if (state != Main.WAIT)
            throw new IllegalStateException("state == START");

        while (buf.isReadable()) {
            if (buf.readByte() == Main.START) {//当 state 从 WAIT 变成 START
                state = Main.START;
                Objects.requireNonNull(client);
                //                PT 的数据转发给 C           后面可能有数据，发送给 C   跳变时移除this
                ctx.pipeline().addFirst(new Transfer(client).writeOnActive(buf)).remove(this);
                return;
            }
        }
        buf.release();
    }
}
