package org.jd.net.http.proxy;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.CompositeByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import org.apache.commons.lang3.StringUtils;
import org.jd.net.core.Buf;
import org.jd.net.core.ChannelEvent;
import org.jd.net.core.DuplexTransfer;
import org.jd.net.core.SplitHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;

/**
 * ====================https============================
 * CONNECT www.qq.com:443 HTTP/1.1[\r][\n]
 * Host: www.qq.com[\r][\n]
 * User-Agent: Apache-HttpClient/4.5.6 (Java/1.8.0_181)[\r][\n]
 * [\r][\n]
 * ====================http============================
 * GET http://www.qq.com/ HTTP/1.1
 * Host: www.qq.com
 * Proxy-Connection: Keep-Alive
 * User-Agent: Apache-HttpClient/4.5.6 (Java/1.8.0_181)
 */
public class HttpProxyService extends SplitHandler {
    static final Logger logger = LoggerFactory.getLogger(HttpProxyService.class);
    private static final byte[] CRLF = new byte[]{'\r', '\n'};

    public HttpProxyService() {
        super("\r\n");
    }

    /**
     * https: init -> connect -> body
     * http:  init -> http -> body
     */
    private State state = State.init;

    private enum State {init, connect, http, body}

    private CompositeByteBuf bufs;
    private ArrayList<ByteBuf> dataToServer = new ArrayList<>();//要发往服务端的数据
    private String host;
    private int port;

    @Override
    protected void decodeFrame(ChannelHandlerContext ctx, ByteBuf frame, ByteBuf remains) throws Exception {

        switch (state) {
            case init://解析 host port
                String line = Buf.readString(frame);
                String[] split = StringUtils.split(line, " ");
                if (split[0].equals("CONNECT")) {// CONNECT www.qq.com:443 HTTP/1.1\r\n
                    state = State.connect;
                    //先告诉客户端连接成功，再进行连接,避免数据等待
                    ctx.writeAndFlush(Buf.wrap("HTTP/1.1 200 Connection Established \r\n\r\n"));
                    setServerAddr(split[1], 443);
                } else {// GET http://www.qq.com:80/index.html HTTP/1.1\r\n
                    state = State.http;
                    String url = split[1];// http://www.qq.com:80/index.html\r\n
                    int i = url.indexOf('/', 7);//端口后面的斜线的坐标
                    setServerAddr(url.substring(7, i), 80);//www.qq.com:80
                    //转换为 GET /index.html HTTP/1.1\r\n
                    dataToServer.add(Buf.wrap(split[0], " ", url.substring(i + 1), " ", split[2]));
                }
                break;
            case connect://忽略剩余请求头
                if (frame.readableBytes() == 2) {//CRLF 2字节
                    state = State.body;
                }
                frame.release();
                break;
            case http://将请求头 Proxy- 开头的去掉 "Proxy-" 前缀
                if (frame.readableBytes() > 2) {//处理请求头
                    if (frame.readableBytes() < 34) {//跳过太长的请求头，因为请求头太长一般不会以 "Proxy-" 开头
                        String prefix = frame.getCharSequence(frame.readerIndex(), "Proxy-".length(), StandardCharsets.US_ASCII).toString();
                        if (prefix.equalsIgnoreCase("Proxy-")) {//处理Proxy-开头的请求头，例如 Proxy-Connection: Keep-Alive 转为 Connection: Keep-Alive
                            frame.readerIndex(frame.readerIndex() + "Proxy-".length());
                            Buf.print("请求头转换后", frame);
                        }
                    }
                    dataToServer.add(frame);
                } else {//\r\n
                    dataToServer.add(frame);//\r\n
                    state = State.body;
                }
                break;
        }
        if (state == State.body) {//剩余数据发送到服务端

            ctx.channel().config().setAutoRead(false);//停止自动读，等连接到服务端后再继续读
            if (frame.isReadable())
                dataToServer.add(frame.slice());
            ctx.pipeline().addLast(new DuplexTransfer(host, port, ChannelEvent.handlerAdded,
                    new ChannelInboundHandlerAdapter() {
                        @Override
                        public void channelActive(ChannelHandlerContext ctx) throws Exception {
                            dataToServer.forEach(ctx::write);
                            if (remains.isReadable())
                                ctx.write(remains);
                            ctx.flush();

                            dataToServer = null;
                            super.channelActive(ctx);
                        }
                    }));
        }
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (state == State.body) {
            ctx.fireChannelRead(msg);
        } else {
            super.channelRead(ctx, msg);
        }
    }

    /**
     * @param hostPort    www.qq.com:80
     * @param defaultPort 80
     */
    private void setServerAddr(String hostPort, int defaultPort) {
        String[] split = StringUtils.split(hostPort, ":");
        host = split[0];
        port = split.length == 2 ? Integer.valueOf(split[1]) : defaultPort;
    }

}
