package org.jd.net.tcpproxy;

import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import org.jd.net.core.Acceptor;
import org.jd.net.core.Connector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.net.URL;
import java.util.function.Supplier;

public class Main {
    static Logger logger = LoggerFactory.getLogger(Main.class);

    /**
     * 启动添加 -Dio.netty.leakDetectionLevel=paranoid 监控有无内存泄漏
     * -Dio.netty.leakDetectionLevel=disabled 禁用监控
     */
    public static void main(String[] a) throws FileNotFoundException {
        File file = new File("tcp-proxy.conf");
        if (!file.isFile()) {
            URL url = Main.class.getClassLoader().getResource("tcp-proxy.conf");
            if (url != null)
                file = new File(url.getFile());
            if(!file.isFile()){
                logger.info("未找到配置文件 {}", file.getAbsolutePath());
                return;
            }
        }
        BufferedReader reader = new BufferedReader(new FileReader(file));
        reader.lines().forEach(line -> {
            String[] s = line.split(" ");
            if (s.length == 3)
                start(Integer.valueOf(s[0]), s[1], Integer.valueOf(s[2]));
        });
    }

    private static void start(int listenPort, String host, int port) {
        logger.info("启动tcp代理：{} --> {}:{}", listenPort, host, port);
        new Acceptor(listenPort, new Supplier<ChannelHandler[]>() {
            @Override
            public ChannelHandler[] get() {
                return new ChannelInboundHandlerAdapter[]{
                        new ChannelInboundHandlerAdapter() {
                            private volatile Connector target;//被代理的目标

                            @Override
                            public void channelActive(ChannelHandlerContext client) throws Exception {
                                client.channel().config().setAutoRead(false);//暂停读取
                                target = new Connector(host, port, new Supplier<ChannelHandler[]>() {
                                    @Override
                                    public ChannelHandler[] get() {
                                        return new ChannelInboundHandlerAdapter[]{
                                                new ChannelInboundHandlerAdapter() {
                                                    @Override
                                                    public void channelActive(ChannelHandlerContext ctx) throws Exception {
                                                        client.channel().config().setAutoRead(true);//恢复读取
                                                    }

                                                    @Override
                                                    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
                                                        client.close();
                                                    }

                                                    @Override
                                                    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
//                                                        logger.info("client <-- target : {}", ((ByteBuf) msg).readableBytes());
                                                        client.writeAndFlush(msg);
                                                    }
                                                }
                                        };
                                    }
                                });
                                new Thread(target).start();
                            }

                            @Override
                            public void channelInactive(ChannelHandlerContext ctx) throws Exception {
                                if (target != null)
                                    target.close();
                            }

                            @Override
                            public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
//                                logger.info("client --> target : {}", ((ByteBuf) msg).readableBytes());
                                target.channel().writeAndFlush(msg);
                            }
                        }
                };
            }
        }).run();
    }
}
