package org.jd.tcp.bridge;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 代理服务端 PS
 * 代理目标端 PT
 */
public class Main {
    static Logger logger = LoggerFactory.getLogger(Main.class);

    public static void main(String[] a) {
        if ("-s".equalsIgnoreCase(a[0])) {// -s clientPort targetPort
            serverStart(Integer.valueOf(a[1]), Integer.valueOf(a[2]));
        } else {// port serverHost serverPort password
            targetStart(Integer.valueOf(a[0]), a[1], Integer.valueOf(a[2]), a[3]);
        }
    }


    static void serverStart(int clientPort, int targetPort) {

    }

    static void targetStart(int port, String sHost, int sPort, String password) {


    }
}
