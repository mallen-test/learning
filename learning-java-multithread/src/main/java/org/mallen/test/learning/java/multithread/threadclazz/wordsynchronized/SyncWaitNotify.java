package org.mallen.test.learning.java.multithread.threadclazz.wordsynchronized;

import java.util.concurrent.TimeUnit;

/**
 * @author mallen
 * @date 3/20/20
 */
public class SyncWaitNotify {

    public static void main(String[] args) throws InterruptedException {
        SyncWaitNotify lock = new SyncWaitNotify();
        WaitThread  waitThread = new WaitThread(lock);
        NotifyThread notifyThread = new NotifyThread(lock);
        // C：操作不同实例
        Thread thread1 = new Thread(waitThread);
        Thread thread2 = new Thread(notifyThread);

        thread1.start();
        thread2.start();
        TimeUnit.SECONDS.sleep(20);
        // 验证证明：在thread2未释放锁之前，interrupt方法调用了之后，wait不会抛出异常。但是中断状态为true
        thread1.interrupt();
        System.out.println("WaitThread interrupt");
        thread2.join();
    }

    static class WaitThread implements Runnable {
        final SyncWaitNotify lock;

        WaitThread(SyncWaitNotify lock) {
            this.lock = lock;
        }

        @Override
        public void run() {
            synchronized (lock) {
                try {
                    System.out.println("WaitThread Get Lock");
                    TimeUnit.SECONDS.sleep(3);
                    System.out.println("WaitThread call wait");
                    lock.wait();
                    System.out.println("WaitThread working");
                } catch (InterruptedException e) {
                    System.out.println("WaitThread Interrupted");
                }
                System.out.println("WaitThread中断状态：" + Thread.currentThread().isInterrupted());
            }
        }
    }

    static class NotifyThread implements Runnable {
        final SyncWaitNotify lock;

        NotifyThread(SyncWaitNotify lock) {
            this.lock = lock;
        }

        @Override
        public void run() {
            try {
                TimeUnit.SECONDS.sleep(2);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            System.out.println("NotifyThread wakeup");
            synchronized (lock) {
                System.out.println("NotifyThread Get Lock");
                lock.notify();
                System.out.println("NotifyThread sleep");
                try {
                    TimeUnit.SECONDS.sleep(20);
                    System.out.println("NotifyThread sleep finish");
                } catch (InterruptedException e) {

                }

            }
            System.out.println("NotifyThread exit sync block");
        }
    }
}
