package org.jd.net.http.proxy;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.CompositeByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import org.apache.commons.lang3.StringUtils;
import org.jd.net.netty.Buf;
import org.jd.net.netty.Netty;
import org.jd.net.netty.handler.Handlers;
import org.jd.net.netty.handler.SplitHandler;
import org.jd.net.netty.handler.Transfer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;

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

    public HttpProxyService() {
        super("\r\n");
    }

    /**
     * https: init -> connect -> body
     * http:  init -> http -> body
     */
    private State state = State.init;

    private enum State {init, connect, http, body}

    private CompositeByteBuf headers = Buf.alloc.compositeBuffer();//要发往服务端的头部数据
    private String host;
    private int port;

    @Override
    protected void decodeFrame(ChannelHandlerContext ctx, ByteBuf frame, ByteBuf remains) throws Exception {

        switch (state) {
            case init://解析 host port
                String line = Buf.readString(frame);
                frame.release();
                logger.info(line);
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
                    headers.addComponent(true, Buf.wrap(split[0], " ", url.substring(i), " ", split[2]));
                }
                break;
            case connect://忽略剩余请求头
                if (frame.readableBytes() == 2) {//CRLF 2字节
                    state = State.body;
                }
//                logger.info("connect head: {}", Buf.readString(frame));
                frame.clear().release();
                break;
            case http://将请求头 Proxy- 开头的去掉 "Proxy-" 前缀
                if (frame.readableBytes() > 2) {//处理请求头
                    if (frame.readableBytes() < 34) {//跳过太长的请求头，因为请求头太长一般不会以 "Proxy-" 开头
                        String prefix = frame.getCharSequence(frame.readerIndex(), "Proxy-".length(), StandardCharsets.US_ASCII).toString();
                        if (prefix.equalsIgnoreCase("Proxy-")) {//处理Proxy-开头的请求头，例如 Proxy-Connection: Keep-Alive 转为 Connection: Keep-Alive
                            frame.readerIndex(frame.readerIndex() + "Proxy-".length());
                            logger.info("请求头转换后 {}", Buf.toString(frame));
                        }
                    }
                    headers.addComponent(true, frame);
                } else {//\r\n
                    headers.addComponent(true, frame);//\r\n
                    state = State.body;
                }
                break;
        }
        if (state == State.body) {//剩余数据发送到服务端
            headers.addComponent(true, remains.retainedSlice());
            remains.release();

            Channel client = ctx.channel();
            client.config().setAutoRead(false);//暂停自动读，等连接到服务器再继续
            client.pipeline().remove(this);

            Netty.connect(host, port, server -> {
                server.pipeline().addLast(Transfer.autoReadOnActive(client),
                        Handlers.active(s -> server.writeAndFlush(headers)),//头部数据发送到服务端
                        Handlers.closeOnIOException);
                client.pipeline().addLast(new Transfer(server));//已经有 Handlers.closeOnIOException 了
            });
        }
    }

    @Override
    public void handlerAdded(ChannelHandlerContext ctx) throws Exception {
        ctx.pipeline().addLast(Handlers.closeOnIOException);
        super.handlerAdded(ctx);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        headers.release();
        super.exceptionCaught(ctx, cause);
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
