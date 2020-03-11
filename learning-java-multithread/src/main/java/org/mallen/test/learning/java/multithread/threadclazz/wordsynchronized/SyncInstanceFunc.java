package org.mallen.test.learning.java.multithread.threadclazz.wordsynchronized;

/**
 * @author mallen
 * @date 3/2/20
 */
public class SyncInstanceFunc implements Runnable {
    // A：静态变量，线程之间共享
    public static int i;
    // B：修饰实例方法，运行该方法时需要先获取实例的锁，同一时间只有一个线程获取成功
    public synchronized void inc() {
        i++;
    }

    @Override
    public void run() {
        for (int j = 0; j < 10000; j++) {
            inc();
        }
    }

    public static void main(String[] args) throws InterruptedException {
        // C：对同一个实例进行操作
        SyncInstanceFunc instance = new SyncInstanceFunc();
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
