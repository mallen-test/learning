package org.mallen.test.learning.dl.mysql.optimistic;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CountDownLatch;

/**
 * 测试基于mysql的乐观锁实现，与其他锁不同，乐观锁一般会将资源保存在数据库，然后多个线程去竞争数据库资源。
 * 所以，对于卖票程序，我们不再使用全局变量来表示票数，而是将票数存放在数据库。
 *
 * 运行该代码前请创建对应数据库，并使用resources/MysqlOptimisticLock.sql初始化数据库表
 * @author mallen
 * @date 2/24/20
 */
public class OptimisticLockTest {
    private static HikariDataSource hikariDataSource;
    /**
     * 收集已卖票信息，如果正常的话，set的size应该等于tickets
     */
    private static Set<Integer> soldTickets = new HashSet(100, 1);

    public static void main(String[] args) throws InterruptedException {
        hikariDataSource = createDatasource();

        int sellerCount = 3;
        CountDownLatch latch = new CountDownLatch(sellerCount);
        for (int i = 0; i < sellerCount; i++) {
            Thread thread = new Thread(new OptimisticLockTest.Seller(latch, new OptimisticLock(hikariDataSource)));
            thread.setName("thread" + (i + 1));
            thread.start();
        }
        latch.await();
        // 打印卖出的票的数量，确认是否为100
        System.out.println("已买票数量为：" + soldTickets.size());
    }

    static class Seller implements Runnable {
        private final CountDownLatch latch;
        private final OptimisticLock lock;


        public Seller(CountDownLatch latch, OptimisticLock lock) {
            this.latch = latch;
            this.lock = lock;
        }

        @Override
        public void run() {
            while (true) {
                // 查询数据
                OptimisticLock.Tickets tickets = lock.query();
                if (null == tickets || 1 > tickets.getResource()) {
                    break;
                }
                // 计算
                int resource = tickets.getResource();
                tickets.setResource(--resource);
                // CAS
                cas(tickets);
            }
            latch.countDown();
        }

        private void cas(OptimisticLock.Tickets tickets) {
            boolean success = lock.update(tickets);
            if (success) {
                int ticket = tickets.getResource() + 1;
                System.out.println(Thread.currentThread().getName() + "卖出票：" + ticket);
                soldTickets.add(ticket);
            }
        }
    }

    private static HikariDataSource createDatasource() {
        HikariConfig hikariConfig = new HikariConfig();
        hikariConfig.setJdbcUrl("jdbc:mysql://127.0.0.1:3306/DL_TEST?useUnicode=true&characterEncoding=utf-8&useSSL=false&verifyServerCertificate=false&allowPublicKeyRetrieval=true");
        hikariConfig.setUsername("root");
        hikariConfig.setPassword("root");

        return new HikariDataSource(hikariConfig);
    }
}
