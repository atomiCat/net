package org.jd.net.http.proxy;

public class Main {
    public static void main(String[] a) {
        if ("-s".equalsIgnoreCase(a[0])) {// -s port password
            serverStart(Integer.valueOf(a[1]), Byte.valueOf(a[2]));
        } else {// port serverHost serverPort password
            clientStart(Integer.valueOf(a[0]), a[1], Integer.valueOf(a[2]), Byte.valueOf(a[3]));
        }
    }

    static void clientStart(int port, String sHost, int sPort, byte password) {

//        new Acceptor(port, () -> new ChannelHandler[]{
//                new ChannelInboundHandlerAdapter() {
//                    @Override
//                    public void channelActive(ChannelHandlerContext browser) throws Exception {
//
//                    }
//                }
//        });
    }

    static void serverStart(int port, byte password) {

    }
}
