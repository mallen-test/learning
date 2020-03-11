package org.mallen.test.learning.java.multithread.threadclazz;

import java.util.Random;
import java.util.concurrent.TimeUnit;

/**
 * Thread.stop()方法已经被废弃，为什么呢？（详细可以参见：https://docs.oracle.com/javase/9/docs/api/java/lang/doc-files/threadPrimitiveDeprecation.html）
 * 个人理解：因为stop方法会在任何线程运行的任何阶段中止线程，并且会导致线程锁定的资源被释放，就会导致资源处于一种未知的状态。
 * 当这些处于未知状态的资源被线程获取时，可能会对其他线程造成不可预知的后果。
 * 并且出现这种后果往往都是很难察觉的，所以建议不再使用Thread.stop()方法。
 * <p>
 * 那么推荐的做法是什么呢？
 * 官方推荐的方式是：使用一个volatile变量来标识，并在run方法中判断该标识
 * <p>
 * 注意：这种实现方式，需要自定义释放资源的代码，
 * @author mallen
 * @date 2/28/20
 */
public class StopThread {

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
        // 关闭线程
        toBeStop.shutdown();
        toBeStop.shutdown();

        System.out.println("调用完毕shutdown：" + System.currentTimeMillis());
    }

    public static class ToBeStop implements Runnable {
        private volatile boolean stop = false;
        private Thread thisThread = null;

        @Override
        public void run() {
            thisThread = Thread.currentThread();
            int i = 0;
            while (!stop) {
                try {
                    System.out.println(++i);
                    TimeUnit.MILLISECONDS.sleep(819);
                } catch (InterruptedException e) {
                    System.out.println("收到Interrupted消息：" + System.currentTimeMillis());
                    // 抛出InterruptedException异常后，线程的interrupt标识会被置为false
                    System.out.println("收到InterruptedException异常后的interrupt标志：" + Thread.currentThread().isInterrupted());
                }
            }
        }

        public void shutdown() {
            // 设置标识为关闭
            stop = true;
            // 如果线程中有长时间等待的操作（waits for long periods），比如：sleep、等待输入。则同时需要使用interrupt方法
            thisThread.interrupt();
            // 如果线程不响应interrupt方法怎么办？官方坦言：Unfortunately, there really isn't any technique that works in general
            // 也就是没有一个可靠的通用方式来解决这个问题，应用可以根据自己的特点来确认是否能找到合适的方式
        }
    }
}
