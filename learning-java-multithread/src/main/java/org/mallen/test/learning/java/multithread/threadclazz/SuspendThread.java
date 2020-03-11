package org.mallen.test.learning.java.multithread.threadclazz;

import java.util.Random;
import java.util.concurrent.TimeUnit;

/**
 * Thread.suspend和Thread.resume两个方法被废弃了，为什么呢？
 * 因为suspend容易造成死锁！！！当suspend的时候仍然持有资源的话，其他线程就获取不了，如果唤醒这个线程的前提是先获取其持有的资源，那么就会造成死锁。
 *
 * <p>
 * 那么推荐的做法是什么呢？
 * 使用一个变量来标识，并在run方法中判断
 *
 * @author mallen
 * @date 2/28/20
 */
public class SuspendThread {

    public static void main(String[] args) throws InterruptedException {
        // 启动线程
        ToBeStop toBeStop = new ToBeStop();
        Thread thread = new Thread(toBeStop);
        thread.start();
        // 睡眠随机时间
        Random random = new Random(System.currentTimeMillis());
        int sleepTime = random.nextInt(10);
        sleepTime = sleepTime <= 0 ? 3 : sleepTime;
        System.out.println("睡眠" + sleepTime + "秒");
        TimeUnit.SECONDS.sleep(sleepTime);
        // 开始suspend
        System.out.println("开始suspend");
        toBeStop.suspend();
        // 再次睡眠
        sleepTime = random.nextInt(10);
        sleepTime = sleepTime <= 0 ? 3 : sleepTime;
        System.out.println("睡眠" + sleepTime + "秒");
        TimeUnit.SECONDS.sleep(sleepTime);
        // 开始唤醒
        System.out.println("开始resume");
        toBeStop.resume();
        // 再次睡眠
        sleepTime = random.nextInt(10);
        sleepTime = sleepTime <= 0 ? 3 : sleepTime;
        System.out.println("睡眠" + sleepTime + "秒");
        TimeUnit.SECONDS.sleep(sleepTime);
        // 再次suspend
        System.out.println("再次suspend");
        toBeStop.suspend();
        // 再次睡眠
        sleepTime = random.nextInt(10);
        sleepTime = sleepTime <= 0 ? 3 : sleepTime;
        System.out.println("睡眠" + sleepTime + "秒");
        TimeUnit.SECONDS.sleep(sleepTime);
        // 开始stop
        toBeStop.shutdown();
        System.out.println("stop：" + System.currentTimeMillis());
    }

    public static class ToBeStop implements Runnable {
        private volatile boolean stop = false;
        private volatile boolean suspended = false;
        private Thread thisThread = null;

        @Override
        public void run() {
            thisThread = Thread.currentThread();
            int i = 0;
            while (!stop) {
                try {
                    System.out.println(++i);
                    TimeUnit.MILLISECONDS.sleep(819);
                    if (suspended) {
                        synchronized (this) {
                            while (suspended && !stop) {
                                // wait会释放资源this，所以synchronized方法可以执行
                                wait();
                            }
                        }
                    }
                } catch (InterruptedException e) {
                    System.out.println("收到Interrupted消息：" + System.currentTimeMillis());
                }
            }
        }

        public synchronized void shutdown() {
            // 设置标识为关闭
            stop = true;
            // 如果线程中有长时间等待的操作（waits for long periods），比如：sleep、等待输入。则同时需要使用interrupt方法
            // interrupt同时会让wait()抛出InterruptedException
            thisThread.interrupt();
            // 如果线程不响应interrupt方法怎么办？官方坦言：Unfortunately, there really isn't any technique that works in general
            // 也就是没有一个可靠的通用方式来解决这个问题，应用可以根据自己的特点来确认是否能找到合适的方式
        }

        public synchronized void suspend() {
            suspended = true;
        }

        public synchronized void resume() {
            suspended = false;
            notify();
        }
    }
}
