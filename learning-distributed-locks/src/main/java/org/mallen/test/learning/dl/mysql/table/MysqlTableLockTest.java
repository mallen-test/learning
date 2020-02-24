package org.mallen.test.learning.dl.mysql.table;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * 运行该代码前请创建对应数据库，并使用resources/MysqlTableLock.sql初始化数据库表
 *
 * @author mallen
 * @date 2/21/20
 */
public class MysqlTableLockTest {
    private static Integer tickets = 100;
    /**
     * 收集已卖票信息，如果正常的话，set的size应该等于tickets
     */
    private static Set<Integer> soldTickets = new HashSet(100, 1);
    private static HikariDataSource hikariDataSource;

    public static void main(String[] args) throws InterruptedException {
        hikariDataSource = createDatasource();
        int lockId = 1;

        int sellerCount = 3;
        CountDownLatch latch = new CountDownLatch(sellerCount);
        for (int i = 0; i < sellerCount; i++) {
            Thread thread = new Thread(new MysqlTableLockTest.Seller(latch, new MysqlTableLock(hikariDataSource, lockId)));
            thread.setName("thread" + (i + 1));
            thread.start();
        }
        latch.await();
        // 打印卖出的票的数量，如果与tickets的数量不一样，说明存在多线程资源竞争的问题。如果结果为100，请多次运行，只要有一次不为100，则说明存在资源竞争问题。
        System.out.println("已买票数量为：" + soldTickets.size());
    }

    static class Seller implements Runnable {

        private final CountDownLatch latch;
        private MysqlTableLock lock;

        public Seller(CountDownLatch latch, MysqlTableLock lock) {
            this.latch = latch;
            this.lock = lock;
        }

        @Override
        public void run() {
            while (true) {
                if (lock.tryLock()) {
                    if (tickets > 0) {
                        soldTickets.add(tickets);
                        System.out.println(Thread.currentThread().getName() + "卖出票：" + tickets);
                        tickets--;
                        lock.unlock();
                    } else {
                        lock.unlock();
                        break;
                    }
                    // 增加睡眠时间，以便能给其他线程让出运行时间
                    try {
                        TimeUnit.MILLISECONDS.sleep(50);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                } else {
                    // 未获取到锁，睡眠一段时间，然后再自旋获取锁
                    try {
                        TimeUnit.MILLISECONDS.sleep(50);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            }
            latch.countDown();
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
