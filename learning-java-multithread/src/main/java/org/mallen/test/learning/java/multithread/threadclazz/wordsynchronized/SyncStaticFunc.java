package org.mallen.test.learning.java.multithread.threadclazz.wordsynchronized;

/**
 * @author mallen
 * @date 3/2/20
 */
public class SyncStaticFunc implements Runnable {
    // A：静态变量，线程之间共享
    public static int i;
    // B：修饰静态方法，运行该方法前，需要先获取类的锁
    public static synchronized void inc() {
        i++;
    }

    @Override
    public void run() {
        for (int j = 0; j < 10000; j++) {
            inc();
        }
    }

    public static void main(String[] args) throws InterruptedException {
        SyncStaticFunc staticFunc1 = new SyncStaticFunc();
        SyncStaticFunc staticFunc2 = new SyncStaticFunc();
        // C：操作不同实例
        Thread thread1 = new Thread(staticFunc1);
        Thread thread2 = new Thread(staticFunc2);

        thread1.start();
        thread2.start();
        thread1.join();
        thread2.join();

        // D：最后值为20000
        System.out.println("i的值为：" + i);
    }
}
