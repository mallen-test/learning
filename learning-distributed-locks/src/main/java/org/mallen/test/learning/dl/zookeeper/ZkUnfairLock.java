package org.mallen.test.learning.dl.zookeeper;

import org.apache.commons.lang3.StringUtils;
import org.apache.zookeeper.*;
import org.apache.zookeeper.data.ACL;
import org.apache.zookeeper.data.Stat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;

/**
 * @author mallen
 * @date 2/25/20
 */
public class ZkUnfairLock implements Watcher {
    private static final Logger LOGGER = LoggerFactory.getLogger(ZkUnfairLock.class);
    private final ZooKeeper zkClient;
    private final String lockPath;
    private final List<ACL> acl;
    private String lockNode;
    private CountDownLatch syncPoint;
    private boolean holdsLock = false;
    private String locker;

    public ZkUnfairLock(ZooKeeper zkClient, String lockPath, List<ACL> acl) {
        this.zkClient = zkClient;
        this.lockPath = lockPath;
        this.acl = acl;
        this.syncPoint = new CountDownLatch(1);
        this.lockNode = lockPath + "/unfair_lock";
        this.locker = UUID.randomUUID().toString() + "_" + Thread.currentThread().getId();
    }

    public ZkUnfairLock(ZooKeeper zkClient, String lockPath) {
        this(zkClient, lockPath, ZooDefs.Ids.OPEN_ACL_UNSAFE);
    }

    public synchronized void lock() {
        if (holdsLock) {
            throw new LockingException("已经持有锁了，请先释放锁");
        }

        try {
            // 确认上层节点是否存在
            ZkUtils.ensurePath(zkClient, acl, lockPath);
            LOGGER.debug("Working with locking path:" + lockPath);
            // 检测并获取锁
            checkForLock();
        } catch (KeeperException e) {
            throw new LockingException("获取锁时出现KeeperException", e);
        } catch (InterruptedException e) {
            cancelAttempt();
            throw new LockingException("获取锁时出现InterruptedException", e);
        }
    }

    public synchronized void unlock() throws LockingException {
        try {
            // Try aborting!
            if (!holdsLock) {
                LOGGER.info("没有获取到锁!请先获取锁");
            } else {
                byte[] data = zkClient.getData(lockNode, false, null);
                if (new String(data).equals(locker)) {
                    // 删除节点
                    zkClient.delete(lockNode, -1);
                }
            }
        } catch (KeeperException e) {
            throw new LockingException("删除锁时出现KeeperException", e);
        } catch (InterruptedException e) {
            throw new LockingException("删除锁时出现InterruptedException", e);
        }
        cancelAttempt();
    }

    private void checkForLock() {
        try {
            zkClient.create(lockNode, locker.getBytes(), acl, CreateMode.EPHEMERAL);
            holdsLock = true;
        } catch (KeeperException e) {
            // 节点已经存在，表示其他线程已经获取到锁
            if (e.code().equals(KeeperException.Code.NODEEXISTS)) {
                waitForLock();
            } else {
                throw new LockingException("获取锁时出现KeeperException", e);
            }
        } catch (InterruptedException e) {
            cancelAttempt();
            throw new LockingException("获取锁时出现InterruptedException", e);
        }
    }

    private void waitForLock() {
        Stat stat = null;
        try {
            // 判断锁是否存在
            stat = zkClient.exists(lockNode, this);
            if (stat == null) {
                // 不存在，尝试获取锁
                checkForLock();
            } else {
                // 存在，等待锁删除
                syncPoint.await();
                // 锁已删除，尝试获取锁
                checkForLock();
            }
        } catch (KeeperException e) {
            throw new LockingException("获取锁时出现KeeperException", e);
        } catch (InterruptedException e) {
            cancelAttempt();
            throw new LockingException("获取锁时出现InterruptedException", e);
        }

    }

    private synchronized void cancelAttempt() {
        LOGGER.info("取消获取锁!");
        holdsLock = false;
        syncPoint.countDown();
        syncPoint = new CountDownLatch(1);
    }

    @Override
    public void process(WatchedEvent event) {
        if (!event.getPath().equals(lockNode)) {
            return;
        }

        if (event.getType() == Watcher.Event.EventType.None) {
            switch (event.getState()) {
                case SyncConnected:
                    LOGGER.info("重连中...");
                    break;
                case Expired:
                    LOGGER.warn("zookeeper session失效了");
                    cancelAttempt();
                    break;
            }
        } else if (event.getType() == Event.EventType.NodeDeleted) {
            syncPoint.countDown();
        } else {
            // 不需要的zookeeper事件
//            LOGGER.warn("不需要的zookeeper事件: {}", event.getType().name());
        }
    }
}
