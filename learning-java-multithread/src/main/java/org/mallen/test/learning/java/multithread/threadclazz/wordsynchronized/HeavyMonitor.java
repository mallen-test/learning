package org.mallen.test.learning.java.multithread.threadclazz.wordsynchronized;

/**
 * 如下代码，每次肯定是C线程先获取到锁，因为synchronized肯定会膨胀为重量级锁，重量级锁的特性就是：当EntryList为空时，是后来的线程先获取锁（EntryList可以看作是调用了wait()方法后的线程队列）。
 *
 *
 * @author mallen
 * @date 3/20/20
 */
public class HeavyMonitor {
    public static void main(String[] args) {

        HeavyMonitor syncDemo1 = new HeavyMonitor();
        syncDemo1.startThreadA();
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        syncDemo1.startThreadB();
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        syncDemo1.startThreadC();


    }

    final Object lock = new Object();


    public void startThreadA() {
        new Thread(() -> {
            synchronized (lock) {
                System.out.println("A get lock");
                try {
                    Thread.sleep(500);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                System.out.println("A release lock");
            }
        }, "thread-A").start();
    }

    public void startThreadB() {
        new Thread(() -> {
            synchronized (lock) {
                System.out.println("B get lock");
            }
        }, "thread-B").start();
    }

    public void startThreadC() {
        new Thread(() -> {
            synchronized (lock) {

                System.out.println("C get lock");
            }
        }, "thread-C").start();
    }
}
