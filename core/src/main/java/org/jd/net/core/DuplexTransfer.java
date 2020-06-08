package org.jd.net.core;

import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DuplexTransfer extends ChannelInboundHandlerAdapter {
    private static final Logger logger = LoggerFactory.getLogger(DuplexTransfer.class);
    private final String host;
    private final int port;
    private final ChannelEvent connectOn;
    private final ChannelHandler[] hostPortHandler;

    /**
     * @param host      host
     * @param port      port
     * @param connectOn 连接host:port的时机,only handlerAdded or channelActive
     */
    public DuplexTransfer(String host, int port, ChannelEvent connectOn, ChannelHandler... hostPortHandler) {
        this.host = host;
        this.port = port;
        assert connectOn == ChannelEvent.handlerAdded || connectOn == ChannelEvent.channelActive;
        this.connectOn = connectOn;
        this.hostPortHandler = hostPortHandler;
    }

    public DuplexTransfer(String host, int port) {
        this(host, port, ChannelEvent.channelActive);
    }

    @Override
    public void handlerAdded(ChannelHandlerContext ctx) throws Exception {
        if (connectOn == ChannelEvent.handlerAdded)
            connect(ctx);
        super.handlerAdded(ctx);
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        if (connectOn == ChannelEvent.channelActive)
            connect(ctx);
        ctx.fireChannelActive();
    }

    ChannelHandlerContext hostPort;

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        if (hostPort != null && hostPort.channel().isActive())
            hostPort.close();
    }

    private void connect(ChannelHandlerContext ctx) {
        Netty.connect(host, port, new ChannelInboundHandlerAdapter() {
            @Override
            public void channelRegistered(ChannelHandlerContext hostPort) throws Exception {
                if (hostPortHandler != null)
                    hostPort.pipeline().addLast(hostPortHandler);

                hostPort.pipeline().addLast(
                        new ChannelInboundHandlerAdapter() {
                            @Override
                            public void channelActive(ChannelHandlerContext hostPort) throws Exception {
                                DuplexTransfer.this.hostPort = hostPort;
                                ctx.pipeline().addLast(new Transfer(hostPort));//ctx数据复制到hostPort
                                ctx.channel().config().setAutoRead(true);
                                hostPort.fireChannelActive();
                            }
                        },
                        new Transfer(ctx)//hostPort数据复制到ctx
                );
            }
        }).addListener(future -> {
            if (!future.isSuccess())
                ctx.close();//连接失败，关闭
        });
    }
}
