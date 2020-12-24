package org.muweng.utils;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicIntegerArray;
import java.util.function.Consumer;

/**
 * @author: muweng
 * @date: 2020/12/11 15:59
 * @description: 环形时间轮, 参考crossoverJie的RingBufferWheel
 * @see <a href='https://github.com/crossoverJie/cim/blob/186ed8aaf5cb438c6e865c36c3580ad379ca3034/cim-common/src/main/java/com/crossoverjie/cim/common/data/construct/RingBufferWheel.java'>crossoverJie</a>
 */
@Slf4j
public class RingWheelTimer {

    /**
     * 订阅者
     */
    private Consumer consumer;
    /**
     * 暂停标志
     */
    private volatile boolean stop = false;

    /**
     * 默认时间轮大小
     */
    private static final int STATIC_RING_SIZE = 64;
    /**
     * 时间轮大小
     */
    private int bufferSize;

    /**
     * 时间轮存储
     */
    private Object[] ringBuffer;

    /**
     * 启动标志
     */
    private volatile AtomicBoolean start = new AtomicBoolean(false);

    /**
     * 指针游标
     */
    private AtomicInteger index = new AtomicInteger();

    /**
     * 分片锁
     */
    private AtomicIntegerArray segments;

    /**
     * 对象索引表
     */
    private Map<String, Node> idxMap = new ConcurrentHashMap<>(16);

    public RingWheelTimer(Consumer consumer) {
        this.consumer = consumer;
        this.bufferSize = STATIC_RING_SIZE;
        this.ringBuffer = new Object[bufferSize];
        this.segments = new AtomicIntegerArray(bufferSize);
    }

    public RingWheelTimer() {
        this.bufferSize = STATIC_RING_SIZE;
        this.ringBuffer = new Object[bufferSize];
        this.segments = new AtomicIntegerArray(bufferSize);
    }

    /**
     * 启动时间轮
     */
    public void start() {
        if (!start.get()) {
            if (start.compareAndSet(start.get(), true)) {
                log.info("Delay task is starting");
                Thread thread = new Thread(new TimerDevice());
                thread.setName("RingWheelTimer Thread");
                thread.start();
            }
        }
    }

    /**
     * 获取存入对象
     * @param key
     * @return
     */
    public Object getValue(String key){
        Optional<Node> optional = Optional.ofNullable(idxMap.get(key));
        return optional.isPresent()?optional.get().getValue():null;
    }

    /**
     * 对象剩余时间，单位秒
     * @param key
     * @return
     */
    public int ttl(String key){
        Optional<Node> optional = Optional.ofNullable(idxMap.get(key));
        return optional.map(node -> (node.cycleNum * bufferSize + node.idx) - index.get()).orElse(0);
    }

    /**
     * 添加对象
     *
     * @param delay 时长
     * @param key   键
     * @param value 值
     */
    public void add(int delay, String key, Object value) {
        //索引坐标
        int idx = mod(delay, bufferSize);
        try {
            if (segments.compareAndSet(idx, 0, 1)) {
                int cycle = cycleNum(delay, bufferSize);
                Set<Node> sets = get(idx);
                Node node;
                if (Objects.nonNull(sets)) {
                    node = new Node(idx, cycle, key, value);
                    sets.add(node);
                } else {
                    node = new Node(idx, cycle, key, value);
                    sets = new HashSet<>();
                    sets.add(node);
                    put(idx, sets);
                }
                idxMap.put(key, node);
            }
        } finally {
            segments.compareAndSet(idx, 1, 0);
        }

    }

    /**
     * 获取当前时间段上的所有存放对象
     *
     * @param idx
     * @return
     */
    private Set<Node> get(int idx) {
        return (Set<Node>) ringBuffer[idx];
    }

    /**
     * 存放当前时间片段上的所有对象
     *
     * @param idx
     * @param tasks
     */
    private void put(int idx, Set<Node> tasks) {
        ringBuffer[idx] = tasks;
    }

    /**
     * 对象生命时间周期到了，移除
     *
     * @param idx
     * @return
     */
    private Set<Node> remove(int idx) {
        Set<Node> result = new HashSet<>();

        Set<Node> sets = (Set<Node>) ringBuffer[idx];
        if (Objects.isNull(sets)) {
            return result;
        }
        for (Node node : sets) {
            if (node.getCycleNum() == 0) {
                sets.remove(node);
                idxMap.remove(node.getKey());
                result.add(node);
            } else {
                node.decrementCycle();
            }
        }
        if (sets.isEmpty()) {
            ringBuffer[idx] = null;
        }
        return result;
    }

    /**
     * mod方法
     *
     * @param target
     * @param mod
     * @return
     */
    private int mod(int target, int mod) {
        // equals target % mod
        target = target + index.get();
        return target & (mod - 1);
    }

    private int cycleNum(int target, int mod) {
        //equals target/mod
        return target >> Integer.bitCount(mod - 1);
    }

    /**
     * 定时器
     */
    private class TimerDevice implements Runnable {

        @Override
        public void run() {

            while (!stop) {

                try {
                    //todo 业务处理
                    log.info("运了时间：{}",index.get());
                    Set<Node> tasks = remove(index.get());
                    //订阅
                    if (Objects.nonNull(consumer)) {
                        consumer.accept(tasks);
                    }
                    //重置游标
                    if (index.incrementAndGet() > bufferSize - 1) {
                        index.getAndSet(0);
                    }

                    TimeUnit.SECONDS.sleep(1);
                } catch (InterruptedException e) {
                    log.error("定时器错误：{}", e);
                }
            }
        }
    }

    /**
     * 节点
     */
    @Data
    @AllArgsConstructor
    private class Node {
        /**
         * 坐标
         */
        private int idx;
        /**
         * 圈数
         */
        private int cycleNum;
        /**
         * 查找键
         */
        private String key;
        /**
         * 对应值
         */
        private Object value;

        public void decrementCycle() {
            cycleNum--;
        }

    }

    public static void main(String[] args) throws InterruptedException {
        RingWheelTimer ringWheelTimer = new RingWheelTimer();
        ringWheelTimer.start();
        ringWheelTimer.add(10,"test","10");
        while (true){
            System.out.println("获取到值："+ringWheelTimer.getValue("test"));
            System.out.println("获取到ttl："+ringWheelTimer.ttl("test"));
            Thread.sleep(500);
        }
    }
}
