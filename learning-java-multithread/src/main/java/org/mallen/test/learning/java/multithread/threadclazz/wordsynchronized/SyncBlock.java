package org.mallen.test.learning.java.multithread.threadclazz.wordsynchronized;

import java.util.concurrent.TimeUnit;

/**
 * @author mallen
 * @date 3/2/20
 */
public class SyncBlock implements Runnable {
    // A：静态变量，线程之间共享
    public static int i;

    public void inc() throws InterruptedException {
        // 耗时操作
        beforeInc();
        // B：同步代码块
        synchronized (this) {
            i++;
        }
        // 耗时操作
        afterInc();
    }

    private void afterInc() throws InterruptedException {
        TimeUnit.MILLISECONDS.sleep(1);
    }

    private void beforeInc() throws InterruptedException {
        TimeUnit.MILLISECONDS.sleep(1);
    }

    @Override
    public void run() {
        try {
            for (int j = 0; j < 100000; j++) {
                inc();
            }
        } catch (InterruptedException e) {

        }
    }

    public static void main(String[] args) throws InterruptedException {
        // C：操作同一个实例
        SyncBlock instance = new SyncBlock();
        Thread thread1 = new Thread(instance);
        Thread thread2 = new Thread(instance);

        thread1.start();
        thread2.start();
        thread1.join();
        thread2.join();
        // D：最后值为20000
        System.out.println("i的值为：" + i);
    }
}
