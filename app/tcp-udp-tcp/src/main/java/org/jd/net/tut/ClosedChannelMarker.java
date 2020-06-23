package org.jd.net.tut;

import java.util.Iterator;
import java.util.TreeSet;

/**
 * 保存已经关闭的管道index
 */
public class ClosedChannelMarker {
    /**
     * 保存已经关闭的channelIndex，该集合中存在的channelIndex都已经被关闭
     */
    private static final TreeSet<Integer> closedChannels = new TreeSet<>();
    /**
     * 从0开始的已经关闭的channelIndex最大值，小于等于此值的都已经被关闭
     */
    private static int closedChannelIndexMax = -1;

    static synchronized boolean isClosed(int index) {
        return index <= closedChannelIndexMax || closedChannels.contains(index);
    }

    /**
     * 管道关闭时应该调用此方法
     */
    static synchronized void mark(Integer index) {
        if (closedChannels.add(index)) {
            Iterator<Integer> iterator = closedChannels.iterator();
            while (iterator.hasNext() && closedChannelIndexMax + 1 == iterator.next()) {
                closedChannelIndexMax++;
                iterator.remove();
            }
        }
    }
}
