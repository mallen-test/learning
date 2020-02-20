package org.mallen.test.learning.dl.redis.redisson;

import org.mallen.test.learning.dl.redis.lettuce.LettuceLock;
import org.redisson.Redisson;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.redisson.config.TransportMode;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * 使用Redission客户端，实现分布式锁
 *
 * @author mallen
 * @date 2/19/20
 */
public class RedissonLockTest {
    private static String LOCK_NAME = "tickets_lock";
    private static Integer tickets = 100;
    /**
     * 收集已卖票信息，如果正常的话，set的size应该等于tickets
     */
    private static Set<Integer> soldTickets = new HashSet(100, 1);

    public static void main(String[] args) throws InterruptedException {
        RedissonClient redisson = initRedisson();
        RLock lock = redisson.getLock(LOCK_NAME);

        int sellerCount = 3;
        CountDownLatch latch = new CountDownLatch(sellerCount);
        for (int i = 0; i < sellerCount; i++) {
            Thread thread = new Thread(new RedissonLockTest.Seller(latch, lock));
            thread.setName("thread" + (i + 1));
            thread.start();
        }
        latch.await();
        // 打印卖出的票的数量，如果与tickets的数量不一样，说明存在多线程资源竞争的问题。如果结果为100，请多次运行，只要有一次不为100，则说明存在资源竞争问题。
        System.out.println("已卖票数量为：" + soldTickets.size());

        redisson.shutdown();
    }

    static class Seller implements Runnable {
        private RLock lock;
        private final CountDownLatch latch;

        public Seller(CountDownLatch latch, RLock lock) {
            this.latch = latch;
            this.lock = lock;
        }

        @Override
        public void run() {
            while (true) {
                try {
                    // 尝试获取锁，锁的过期时间为10s
                    if (lock.tryLock(0, 100, TimeUnit.SECONDS)) {
                        try {
                            if (tickets > 0) {
                                soldTickets.add(tickets);
                                System.out.println(Thread.currentThread().getName() + "卖出票：" + tickets);
                                tickets--;
                            } else {
                                break;
                            }
                        } catch (Exception e) {
                            e.printStackTrace();
                        } finally {
                            // 释放锁
                            lock.unlock();
                        }
                        // 如果获取到锁，则在解锁后，睡眠一段时间，给其他线程留出执行机会
                        TimeUnit.MILLISECONDS.sleep(50);
                    } else {
                        // 未获取到锁，睡眠10 ms，然后再自旋获取锁
                        try {
                            TimeUnit.MILLISECONDS.sleep(10);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
            latch.countDown();
        }
    }

    private static RedissonClient initRedisson() {
        Config config = new Config();
        config.useSingleServer().setAddress("redis://127.0.0.1:6379").setDatabase(0);

        return Redisson.create(config);
    }
}
