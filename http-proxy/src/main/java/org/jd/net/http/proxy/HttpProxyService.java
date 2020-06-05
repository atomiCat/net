package org.jd.net.http.proxy;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.LineBasedFrameDecoder;
import org.apache.commons.lang3.StringUtils;
import org.jd.net.core.Buf;
import org.jd.tcp.bridge.message.Message;
import org.jd.tcp.bridge.message.channel.ChannelContext;
import org.jd.tcp.bridge.util.Buf;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.ConcurrentHashMap;

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
public class HttpProxyService extends LineBasedFrameDecoder {
    static final Logger logger = LoggerFactory.getLogger(HttpProxyService.class);


    private State state = State.init;

    public HttpProxyService() {
        super(4096);
    }

    private enum State {init, connect, http, body}


    @Override
    protected Object decode(ChannelHandlerContext browser, ByteBuf buffer) throws Exception {

        Object decode = super.decode(browser, buffer);
        if (decode == null)
            return null;

        ByteBuf frame = (ByteBuf) decode;
//        Buf.print("decode", frame);
        switch (state) {
            case init://建立连接
                String s = Buf.readString(frame);
                frame.release();
                String[] split = s.split(" ");
                if (split[0].equals("CONNECT")) {// CONNECT www.qq.com:443 HTTP/1.1
                    state = State.connect;
                    //先告诉客户端连接成功，再进行连接,避免数据等待
                    browser.writeAndFlush(Buf.wrap("HTTP/1.1 200 Connection Established \r\n\r\n"));
                    serverConnect(split[1], 443);//通知server端连接目标主机
                } else {// GET http://www.qq.com:80/index.html HTTP/1.1
                    state = State.http;
                    String url = split[1];// http://www.qq.com:80/index.html
                    int i = url.indexOf('/', 7);//端口后面的斜线的坐标
                    String host = url.substring(7, i);//www.qq.com:80
                    serverConnect(host, 80);//通知server端连接目标主机

                    StringBuilder sb = new StringBuilder().append(split[0]).append(' ').append(url, i, url.length())
                            .append(' ').append(split[2]).append("\r\n");
                    Message.write(server, Message.ConnectSend, Buf.wrap(context.id), Buf.wrap(Message.Send), Buf.wrap(sb));
                }
                break;
            case connect:
                if (frame.readableBytes() == 0) {
                    state = State.body;
                }
                frame.clear();
                frame.release();
                break;
            case http:
                if (frame.readableBytes() > 0) {//处理请求头
                    if (frame.readableBytes() < 32) {
                        String prefix = frame.getCharSequence(frame.readerIndex(), "Proxy-".length(), StandardCharsets.UTF_8).toString();
                        if (prefix.equalsIgnoreCase("Proxy-")) {//处理Proxy-开头的请求头，例如 Proxy-Connection: Keep-Alive 转为 Connection: Keep-Alive
                            frame.readerIndex(frame.readerIndex() + "Proxy-".length());
                            Buf.print("请求头转换后", frame);
                        }
                    }
//                    logger.info("发送 ConnectSend.Send 命令 id = {} readableBytes = {}", context.id, frame.readableBytes() + 2);
                    Message.write(server, Message.ConnectSend, Buf.wrap(context.id), Buf.wrap(Message.Send), frame, Buf.wrap("\r\n"));
                } else {//请求头处理完毕
                    state = State.body;
//                    logger.info("发送 ConnectSend.Send 命令 id = {} readableBytes = 2 = 回车换行", context.id);
                    Message.flush(server, Message.ConnectSend, Buf.wrap(context.id), Buf.wrap(Message.Send), Buf.wrap("\r\n"));
                }
                break;
        }
        return null;
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (state == State.body) {
            ctx.fireChannelRead(msg);
        } else {
            super.channelRead(ctx, msg);
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
     * @param host        www.qq.com 或 www.qq.com:80
     * @param defaultPort 默认端口
     */
    private void serverConnect(String host, int defaultPort) throws InterruptedException {
        String[] split = StringUtils.split(host, ":");
        if (split.length == 2) {
            host = split[0];
            defaultPort = Integer.valueOf(split[1]);
        }

        ByteBuf data = Buf.alloc.buffer().writeInt(context.id).writeInt(Message.Connect).writeInt(defaultPort);
        data.writeCharSequence(host, StandardCharsets.UTF_8);//host`
        Message.flush(server, Message.ConnectSend, data);//通知server端连接目标主机
    }
}
