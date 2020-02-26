package org.mallen.test.learning.dl.zookeeper;

import org.apache.zookeeper.ZooKeeper;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;

/**
 * @author mallen
 * @date 2/25/20
 */
public class ZkUnfairLockTest {
    private static Integer tickets = 100;
    /**
     * 收集已卖票信息，如果正常的话，set的size应该等于tickets
     */
    private static Set<Integer> soldTickets = new HashSet(100, 1);
    private static final String ZOOKEEPER_CONNECT_STRING = "127.0.01:2181";
    private static final String LOCK_PATH = "/mallen/test/dl";

    public static void main(String[] args) throws InterruptedException, IOException {
        int sellerCount = 3;
        CountDownLatch latch = new CountDownLatch(sellerCount);
        List<ZooKeeper> zooKeepers = new ArrayList<>(sellerCount);
        for (int i = 0; i < sellerCount; i++) {
            ZooKeeper zooKeeper = initZookeeper();
            zooKeepers.add(zooKeeper);
            ZkUnfairLock lock = new ZkUnfairLock(zooKeeper, LOCK_PATH);

            Thread thread = new Thread(new Seller(latch, lock));
            thread.setName("thread" + (i + 1));
            thread.start();
        }
        latch.await();
        for (ZooKeeper zooKeeper : zooKeepers) {
            zooKeeper.close();
        }
        // 打印卖出的票的数量，如果与tickets的数量不一样，说明存在多线程资源竞争的问题。如果结果为100，请多次运行，只要有一次不为100，则说明存在资源竞争问题。
        System.out.println("已卖票数量为：" + soldTickets.size());

    }

    static class Seller implements Runnable {
        private ZkUnfairLock lock;
        private final CountDownLatch latch;

        public Seller(CountDownLatch latch, ZkUnfairLock lock) {
            this.latch = latch;
            this.lock = lock;
        }

        @Override
        public void run() {
            while (true) {
                lock.lock();
                if (tickets > 0) {
                    soldTickets.add(tickets);
                    System.out.println(Thread.currentThread().getName() + "卖出票：" + tickets);
                    tickets--;
                    lock.unlock();
                } else {
                    lock.unlock();
                    break;
                }
            }

            latch.countDown();
        }
    }

    private static ZooKeeper initZookeeper() throws IOException {
        return new ZooKeeper(ZOOKEEPER_CONNECT_STRING, 1500, event -> {
            System.out.println("接收到zookeeper事件：" + event.getType().name());
        });
    }
}
