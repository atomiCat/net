package org.jd.net.http.proxy;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.CompositeByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import org.apache.commons.lang3.StringUtils;
import org.jd.net.core.Buf;
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
public class HttpProxyService extends ChannelInboundHandlerAdapter {
    static final Logger logger = LoggerFactory.getLogger(HttpProxyService.class);
    private static final byte[] CRLF = new byte[]{'\r', '\n'};
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

    protected void decodeLine(ChannelHandlerContext browser, ByteBuf buffer) throws Exception {
        int LF = buffer.indexOf(buffer.readerIndex(), buffer.writerIndex(), CRLF[1]);
        assert buffer.getByte(LF - 1) == CRLF[0];
        int lineLength = LF - buffer.readerIndex() + 1;

        if (LF < 0) {//此次读取没有回车换行符
            (bufs == null ? (bufs = Buf.alloc.compositeBuffer()) : bufs).addComponent(true, buffer);
            return;
        } else {
            if (bufs == null) {
                dealLine(browser, buffer, lineLength);
            } else {
                bufs.addComponent(true, buffer.slice(buffer.readerIndex(), lineLength));
                buffer.readerIndex(LF + 1);
                dealLine(browser, bufs, bufs.readableBytes());
                bufs.release();
                bufs = null;
            }
        }

        if (buffer.isReadable()) {
            decodeLine(browser, buffer);
        } else {
            buffer.release();
        }

    }

    protected void dealLine(ChannelHandlerContext browser, ByteBuf buffer, int lineLength) throws Exception {

        switch (state) {
            case init://解析 host port
                String line = buffer.readCharSequence(lineLength, StandardCharsets.US_ASCII).toString();
                String[] split = StringUtils.split(line, " ");
                if (split[0].equals("CONNECT")) {// CONNECT www.qq.com:443 HTTP/1.1\r\n
                    state = State.connect;
                    //先告诉客户端连接成功，再进行连接,避免数据等待
                    browser.writeAndFlush(Buf.wrap("HTTP/1.1 200 Connection Established \r\n\r\n"));
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
            case connect:
                if (lineLength == 2) {//CRLF 2字节
                    state = State.body;
                }
                break;
            case http://将请求头 Proxy- 开头的去掉 "Proxy-" 前缀
                if (lineLength > 2) {//处理请求头
                    if (lineLength < 34) {//跳过太长的请求头，因为请求头太长一般不会以 "Proxy-" 开头
                        String prefix = buffer.getCharSequence(buffer.readerIndex(), "Proxy-".length(), StandardCharsets.US_ASCII).toString();
                        if (prefix.equalsIgnoreCase("Proxy-")) {//处理Proxy-开头的请求头，例如 Proxy-Connection: Keep-Alive 转为 Connection: Keep-Alive
                            buffer.readerIndex(buffer.readerIndex() + "Proxy-".length());
                            lineLength -= "Proxy-".length();
                            Buf.print("请求头转换后", buffer);
                        }
                    }
                    dataToServer.add(buffer.slice(buffer.readerIndex(), lineLength));
                    buffer.readerIndex(buffer.readerIndex() + lineLength);
                } else {
                    state = State.body;
                }
                break;
        }
        if (state == State.body) {

        }
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (state == State.body) {
            ctx.fireChannelRead(msg);
        } else {
            decode(ctx, (ByteBuf) msg);
        }
    }

    @Override
    public void channelInactive(ChannelHandlerContext client) throws Exception {
        Message.flush(server, Message.Close, Buf.wrap(context.id));
        logger.info("管道关闭 channelInactive id={} targetAddr={}:{}", context.id, context.targetHost, context.targetPort);
        map.remove(context.id).close();// map.remove 只在channelInactive调用即可
        super.channelInactive(client);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        logger.error("ClientAcceptor 异常", cause);
        if (ctx.channel().isActive())
            ctx.close();
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

    /**
     * @param host        www.qq.com 或 www.qq.com:80
     * @param defaultPort 默认端口
     */
    private void serverConnect(String host, int defaultPort) throws InterruptedException {
        String[] split = StringUtils.split(":");
        if (split.length == 2) {
            host = split[0];
            defaultPort = Integer.valueOf(split[1]);
        }

        ByteBuf data = Buf.alloc.buffer().writeInt(context.id).writeInt(Message.Connect).writeInt(defaultPort);
        data.writeCharSequence(host, StandardCharsets.UTF_8);//host`
        Message.flush(server, Message.ConnectSend, data);//通知server端连接目标主机
    }
}
