package org.mallen.test.learning.dl.redis.lettuce;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * 使用Lettuce的api，采用Redis的单实例加锁模式，实现分布式锁
 *
 * @author mallen
 * @date 2/17/20
 */
public class LettuceLockTest {
    private static Integer tickets = 100;
    /**
     * 收集已卖票信息，如果正常的话，set的size应该等于tickets
     */
    private static Set<Integer> soldTickets = new HashSet(100, 1);

    public static void main(String[] args) throws InterruptedException {
        LettuceLock lettuceLock = new LettuceLock();
        int sellerCount = 3;
        CountDownLatch latch = new CountDownLatch(sellerCount);
        for (int i = 0; i < sellerCount; i++) {
            Thread thread = new Thread(new LettuceLockTest.Seller(latch, lettuceLock));
            thread.setName("thread" + (i + 1));
            thread.start();
        }
        latch.await();
        // 打印卖出的票的数量，如果与tickets的数量不一样，说明存在多线程资源竞争的问题。如果结果为100，请多次运行，只要有一次不为100，则说明存在资源竞争问题。
        System.out.println("已卖票数量为：" + soldTickets.size());

        lettuceLock.destory();
    }

    static class Seller implements Runnable {
        private static String LOCK_KEY = "tickets_lock";
        private final CountDownLatch latch;
        private LettuceLock lettuceLock;

        public Seller(CountDownLatch latch, LettuceLock lettuceLock) {
            this.latch = latch;
            this.lettuceLock = lettuceLock;
        }

        @Override
        public void run() {
            while (true) {
                String uuid = UUID.randomUUID().toString();
                if (lettuceLock.tryLock(LOCK_KEY, uuid)) {
                    if (tickets > 0) {
                        soldTickets.add(tickets);
                        System.out.println(Thread.currentThread().getName() + "卖出票：" + tickets);
                        tickets--;
                        // 执行逻辑完毕，释放锁
                        lettuceLock.releaseLock(LOCK_KEY, uuid);
                    } else {
                        lettuceLock.releaseLock(LOCK_KEY, uuid);
                        break;
                    }
                    // 如果获取到锁，则在解锁后，睡眠一段时间，给其他线程留出执行机会
                    try {
                        TimeUnit.MILLISECONDS.sleep(50);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                } else {
                    // 未获取到锁，睡眠一段时间，然后再自旋获取锁
                    try {
                        TimeUnit.MILLISECONDS.sleep(1);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            }
            latch.countDown();
        }
    }
}
