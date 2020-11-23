package org.jd.net.test;


import io.netty.channel.ChannelDuplexHandler;
import io.netty.util.ResourceLeakDetector;
import org.apache.http.HttpHost;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.util.EntityUtils;
import org.jd.net.http.proxy.Main;
import org.jd.net.http.proxy.XorCodec;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.function.Supplier;

public class HttpProxyTest {
    private Logger logger = LoggerFactory.getLogger(HttpProxyTest.class);

    @Before
    public void init() {
//        logger.info("ResourceLeakDetector.Level={}", ResourceLeakDetector.getLevel());
        ResourceLeakDetector.setLevel(ResourceLeakDetector.Level.PARANOID);//设置资源泄露检查等级：最高等级
//        logger.info("ResourceLeakDetector.Level={}", ResourceLeakDetector.getLevel());
    }

    int clientPort = 8000;
    int serverPort = 8001;
    String password = "123456";

    @Test
    public void testClient() throws IOException {

        Supplier<ChannelDuplexHandler> codecSupplier = () -> new XorCodec(password);
        new Thread(() -> Main.serverStart(serverPort, codecSupplier)).start();
        new Thread(() -> Main.clientStart(clientPort, "127.0.0.1", serverPort, codecSupplier)).start();
        CloseableHttpResponse response;
        try (CloseableHttpClient client = HttpClientBuilder.create().setProxy(new HttpHost("127.0.0.1", clientPort)).build()) {
            for (int i = 0; i < 10; i++) {
                response = client.execute(new HttpGet("https://www.baidu.com/"));
                logger.info("response {}", response.getStatusLine());
                EntityUtils.consume(response.getEntity());
//                logger.info(EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8));
            }
            for (int i = 0; i < 10; i++) {//测试 Connection: Keep-Alive 长连接
                response = client.execute(new HttpGet("http://wap.baidu.com/"));
                logger.info("response {}", response.getStatusLine());
                EntityUtils.consume(response.getEntity());
//                logger.info(EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8));
            }
        }

    }

    @Test
    public void testServer() {
        Main.serverStart(serverPort, null);
    }

}
