package org.mallen.test.learning.dl.redis.lettuce.advance;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.*;

/**
 * 使用Lettuce的api，采用Redis的单实例加锁模式，实现分布式锁，与{@link org.mallen.test.learning.dl.redis.lettuce.LettuceLock}相比，主要有如下改进：
 * 1. 增加获取到锁的线程的守护线程，如果业务处理时间大于锁过期时间，则为锁续时间
 * 2. 释放锁时，发送事件告知其他节点。以解决其他节点多次自旋带来的问题
 * TODO：
 * 1.如果获取到锁的节点在发送解锁事件过程中出现问题，等待锁的节点就接收不到消息，从而导致死锁。所在使用了await方法的超时版本，会不会带来性能问题？
 *
 * @author mallen
 * @date 2/17/20
 */
public class LettuceAdvanceLockTest {
    private static Integer tickets = 100;
    /**
     * 收集已卖票信息，如果正常的话，set的size应该等于tickets
     */
    private static Set<Integer> soldTickets = new HashSet(100, 1);

    public static void main(String[] args) throws InterruptedException {
        LettuceAdvanceLock lettuceLock = new LettuceAdvanceLock();
        int sellerCount = 3;
        CountDownLatch latch = new CountDownLatch(sellerCount);
        for (int i = 0; i < sellerCount; i++) {
            Thread thread = new Thread(new LettuceAdvanceLockTest.Seller(latch, lettuceLock));
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
        private LettuceAdvanceLock lettuceLock;
        private ScheduledExecutorService scheduledExecutor = Executors.newScheduledThreadPool(3);

        public Seller(CountDownLatch latch, LettuceAdvanceLock lettuceLock) {
            this.latch = latch;
            this.lettuceLock = lettuceLock;
        }

        @Override
        public void run() {
            String uuid = UUID.randomUUID().toString();
            while (true) {
                if (lettuceLock.tryLock(LOCK_KEY, uuid)) {
                    if (!doWithLock(uuid)) {
                        // 如果票已经卖完，退出while循环
                        break;
                    }
                    // 如果获取到锁，则执行完业务后，睡眠一段时间，给其他线程留出执行机会
                    try {
                        TimeUnit.MILLISECONDS.sleep(50);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                } else {
                    try {
                        CountDownLatch notifier = new CountDownLatch(1);
                        // 未获取到锁，订阅锁解除消息
                        lettuceLock.subReleaseMsg(notifier);
                        // 等待锁解除(为了防止拥有锁的节点在发送解锁消息之前挂掉，此处设置最长等待时间，如果超过该时间，则再次重试)
                        if (!notifier.await(10, TimeUnit.SECONDS)) {
                            // 超时导致不再等待，释放notifier
                            notifier.countDown();
                        }
                        // 取消消息订阅
                        lettuceLock.unsubReleaseMsg();
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            }
            // 释放资源
            lettuceLock.destoryPsub();
            scheduledExecutor.shutdownNow();

            latch.countDown();
        }

        private Boolean doWithLock(String uuid) {
            Boolean continueExe = Boolean.TRUE;
            // 获取到锁，注册一个任务，在锁失效前，增加过期时间
            Future future = addReExpireThread(uuid);
            // 开始卖票逻辑
            if (tickets > 0) {
                soldTickets.add(tickets);
                System.out.println(Thread.currentThread().getName() + "卖出票：" + tickets);
                tickets--;
            } else {
                continueExe = Boolean.FALSE;
            }
            // 执行逻辑完毕，取消续时线程
            future.cancel(false);
            // 执行逻辑完毕，释放锁
            lettuceLock.releaseLock(LOCK_KEY, uuid);
            // 发送解锁消息
            lettuceLock.pubReleaseMsg(uuid);

            return continueExe;
        }

        private ScheduledFuture addReExpireThread(String uuid) {
            ScheduledFuture future = scheduledExecutor.scheduleAtFixedRate(
                    () -> lettuceLock.reExpire(LOCK_KEY, uuid),
                    // 锁过期时间为10秒，所以此处设置为8s为锁续时
                    8, 8, TimeUnit.SECONDS);
            return future;
        }
    }
}
