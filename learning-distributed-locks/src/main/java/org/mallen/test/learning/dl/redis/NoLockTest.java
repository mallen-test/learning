package org.mallen.test.learning.dl.redis;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * 模拟多线程买票业务，如果不加锁，会导致卖票出问题。
 *
 * @author mallen
 * @date 2/17/20
 */
public class NoLockTest {
    private static Integer tickets = 100;
    /**
     * 收集已卖票信息，如果正常的话，set的size应该等于tickets
     */
    private static Set<Integer> soldTickets = new HashSet(100, 1);

    public static void main(String[] args) throws InterruptedException {
        int sellerCount = 3;
        CountDownLatch latch = new CountDownLatch(sellerCount);
        for (int i = 0; i < sellerCount; i++) {
            Thread thread = new Thread(new Seller(latch));
            thread.setName("thread" + (i + 1));
            thread.start();
        }
        latch.await();
        // 打印卖出的票的数量，如果与tickets的数量不一样，说明存在多线程资源竞争的问题。如果结果为100，请多次运行，只要有一次不为100，则说明存在资源竞争问题。
        System.out.println("已买票数量为：" + soldTickets.size());
    }

    static class Seller implements Runnable {
        private final CountDownLatch latch;

        public Seller(CountDownLatch latch) {
            this.latch = latch;
        }

        @Override
        public void run() {
            while (true) {
                if (tickets > 0) {
                    soldTickets.add(tickets);
                    System.out.println(Thread.currentThread().getName() + "卖出票：" + tickets);
                    tickets--;
                } else {
                    break;
                }
                // 增加睡眠时间，以便能给其他线程让出运行时间
                try {
                    TimeUnit.MILLISECONDS.sleep(50);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
            latch.countDown();
        }
    }
}
