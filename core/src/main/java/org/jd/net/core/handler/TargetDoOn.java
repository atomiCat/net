package org.jd.net.core.handler;

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;

/**
 * 用于多个Channel交互辅助类，当前Channel某事件发生时，对 target Channel 做一些事情
 */
public interface TargetDoOn {
    /**
     * call target.close()
     * when channelInactive
     */
    int closeOnInactive = 0x1;
    /**
     * call target.config().setAutoRead(true)
     * when channelActive
     */
    int setAutoReadOnActive = 0x2;

    class Adapter extends ChannelInboundHandlerAdapter implements TargetDoOn {

        protected final Channel target;
        private int things = 0;

        /**
         * 事件发生时，对 target 做一些事情
         */
        public Adapter(Channel target, int... someThings) {
            this.target = target;
            if (someThings != null) {
                for (int thing : someThings) {
                    things |= thing;
                }
            }
        }

        @Override
        public void channelActive(ChannelHandlerContext ctx) throws Exception {
            if ((things & setAutoReadOnActive) != 0)
                target.config().setAutoRead(true);

            super.channelActive(ctx);
        }

        @Override
        public void channelInactive(ChannelHandlerContext ctx) throws Exception {
            if ((things & closeOnInactive) != 0 && target.isActive())
                target.close();

            super.channelInactive(ctx);
        }
    }

}
