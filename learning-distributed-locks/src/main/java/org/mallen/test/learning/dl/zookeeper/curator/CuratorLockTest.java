package org.mallen.test.learning.dl.zookeeper.curator;

import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.framework.recipes.locks.InterProcessMutex;
import org.apache.curator.retry.RetryOneTime;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * @author mallen
 * @date 2/27/20
 */
public class CuratorLockTest {
    private static Integer tickets = 100;
    /**
     * 收集已卖票信息，如果正常的话，set的size应该等于tickets
     */
    private static Set<Integer> soldTickets = new HashSet(100, 1);
    private static final String ZOOKEEPER_CONNECT_STRING = "127.0.01:2181";
    private static final String LOCK_PATH = "/mallen/test/dl/curator";

    public static void main(String[] args) throws InterruptedException {
        int sellerCount = 1;
        CountDownLatch latch = new CountDownLatch(sellerCount);
        List<CuratorFramework> clients = new ArrayList<>();
        for (int i = 0; i < sellerCount; i++) {
            CuratorFramework curatorFramework = initCurator();
            clients.add(curatorFramework);
            InterProcessMutex lock = new InterProcessMutex(curatorFramework, LOCK_PATH);

            Thread thread = new Thread(new Seller(latch, lock));
            thread.setName("thread" + (i + 1));
            thread.start();
        }
        latch.await();
        clients.forEach(curatorFramework -> curatorFramework.close());
        // 打印卖出的票的数量，如果与tickets的数量不一样，说明存在多线程资源竞争的问题。如果结果为100，请多次运行，只要有一次不为100，则说明存在资源竞争问题。
        System.out.println("已卖票数量为：" + soldTickets.size());
    }

    static class Seller implements Runnable {
        private InterProcessMutex lock;
        private final CountDownLatch latch;

        public Seller(CountDownLatch latch, InterProcessMutex lock) {
            this.latch = latch;
            this.lock = lock;
        }

        @Override
        public void run() {
            while (true) {
                try {
                    lock.acquire();
                    if (tickets > 0) {
                        soldTickets.add(tickets);
                        System.out.println(Thread.currentThread().getName() + "卖出票：" + tickets);
                        tickets--;
                        TimeUnit.SECONDS.sleep(1);
                        lock.release();
                    } else {
                        lock.release();
                        break;
                    }
                } catch (Exception e) {
                    System.out.println("获取锁失败");
                    e.printStackTrace();
                }
            }

            latch.countDown();
        }
    }

    public static CuratorFramework initCurator() {
        CuratorFramework curatorFramework = CuratorFrameworkFactory.builder()
                .connectString(ZOOKEEPER_CONNECT_STRING)
                .sessionTimeoutMs(15000)
                .retryPolicy(new RetryOneTime(2000))
                .build();
        curatorFramework.start();
        return curatorFramework;
    }
}
