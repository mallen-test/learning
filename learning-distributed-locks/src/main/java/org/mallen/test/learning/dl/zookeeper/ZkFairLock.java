package org.mallen.test.learning.dl.zookeeper;

import org.apache.commons.lang3.StringUtils;
import org.apache.zookeeper.*;
import org.apache.zookeeper.data.ACL;
import org.apache.zookeeper.data.Stat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 使用zookeeper实现(公平的)分布式锁，需要注意的是：每个线程需要持有一个zookeeper连接，这样zookeeper服务器才能识别是哪个线程持有了锁。
 * 实现参考了twitrer代码：https://github.com/twitter-archive/commons/blob/master/src/java/com/twitter/common/zookeeper/DistributedLockImpl.java
 *
 * @author mallen
 * @date 2/25/20
 */
public class ZkFairLock {
    private static final Logger LOGGER = LoggerFactory.getLogger(ZkFairLock.class);
    private final ZooKeeper zkClient;
    private final String lockPath;
    private boolean holdsLock = false;
    private final List<ACL> acl;
    private LockWatcher watcher;
    private String currentId;
    private String currentNode;
    private String watchedNode;
    private CountDownLatch syncPoint;
    private final AtomicBoolean aborted = new AtomicBoolean(false);

    public ZkFairLock(ZooKeeper zkClient, String lockPath) {
        this(zkClient, lockPath, ZooDefs.Ids.OPEN_ACL_UNSAFE);
    }

    public ZkFairLock(ZooKeeper zkClient, String lockPath, List<ACL> acl) {
        this.zkClient = zkClient;
        this.lockPath = lockPath;
        this.acl = Collections.unmodifiableList(acl);
        this.syncPoint = new CountDownLatch(1);
    }

    public synchronized void lock() throws LockingException {
        if (holdsLock) {
            throw new LockingException("已经持有锁了，请先释放锁");
        }
        try {
            prepare();
            watcher.checkForLock();
            // 等待获取锁成功，死等
            syncPoint.await();
            // 如果获取锁成功，watcher中会设置holdsLock为true
            if (!holdsLock) {
                throw new LockingException("出错了，不能获取到锁");
            }
        } catch (InterruptedException e) {
            cancelAttempt();
            throw new LockingException("获取锁时出现InterruptedException", e);
        } catch (KeeperException e) {
            // No need to clean up since the node wasn't created yet.
            throw new LockingException("获取锁时出现KeeperException", e);
        }
    }

    public synchronized boolean tryLock(long timeout, TimeUnit unit) {
        if (holdsLock) {
            throw new LockingException("已经持有锁了，请先释放锁");
        }
        try {
            prepare();
            watcher.checkForLock();
            boolean success = syncPoint.await(timeout, unit);
            if (!success) {
                // 获取失败，清理
                cancelAttempt();
                return false;
            }
            if (!holdsLock) {
                throw new LockingException("Error, couldn't acquire the lock!");
            }
        } catch (InterruptedException e) {
            cancelAttempt();
            return false;
        } catch (KeeperException e) {
            // No need to clean up since the node wasn't created yet.
            throw new LockingException("KeeperException while trying to acquire lock!", e);
        }

        return true;
    }

    public synchronized void unlock() throws LockingException {
        if (StringUtils.isEmpty(currentId)) {
            throw new LockingException("没有获取锁，不能解锁!");
        }
        // Try aborting!
        if (!holdsLock) {
            aborted.set(true);
            LOGGER.info("没有获取到锁!");
        } else {
            cleanup();
        }
    }

    private synchronized void prepare()
            throws InterruptedException, KeeperException {
        // 确认上层节点是否存在
        ZkUtils.ensurePath(zkClient, acl, lockPath);
        LOGGER.debug("Working with locking path:" + lockPath);

        // 创建当前临时顺序节点
        currentNode = zkClient.create(lockPath + "/member_", null, acl, CreateMode.EPHEMERAL_SEQUENTIAL);

        // 我们关心注册节点的顺序号，该序列号会被用于锁顺序的比对
        if (currentNode.contains("/")) {
            currentId = currentNode.substring(currentNode.lastIndexOf("/") + 1);
        }
        LOGGER.debug("注册ZK节点成功：{}", currentId);
        this.watcher = new LockWatcher();
    }

    private synchronized void cancelAttempt() {
        LOGGER.info("取消获取锁!");
        holdsLock = false;
        syncPoint.countDown();

        cleanup();
    }

    private void cleanup() {
        LOGGER.info("开始清理！");
        if (StringUtils.isBlank(currentId)) {
            throw new LockingException("currentId不能为空");
        }
        try {
            Stat stat = zkClient.exists(currentNode, false);
            if (stat != null) {
                zkClient.delete(currentNode, -1);
            } else {
                LOGGER.warn("调用了cleanup，但是不需要做任何清理");
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        holdsLock = false;
        aborted.set(false);
        currentId = null;
        currentNode = null;
        watcher = null;
        syncPoint = new CountDownLatch(1);
    }

    class LockWatcher implements Watcher {
        /**
         * 检查我创建的节点是否是序列号最小的节点，如果是，则代表锁获取成功
         * 如果不是，则代表其他线程获取了锁，我就监听我之前的那个线程的节点删除事件
         */
        public synchronized void checkForLock() {
            if (StringUtils.isBlank(currentId)) {
                throw new LockingException("currentId can not be blank");
            }

            try {
                List<String> candidates = zkClient.getChildren(lockPath, null);
                List<String> sortedMembers = Collections.unmodifiableList(candidates);
                sortedMembers.sort(Comparator.naturalOrder());

                // 没有任何的子节点，这是一种异常现象
                if (sortedMembers.isEmpty()) {
                    throw new LockingException("错误！节点：" + lockPath + "，必须要存在子字节点");
                }

                int memberIndex = sortedMembers.indexOf(currentId);
                if (memberIndex == 0) {
                    // 如果轮到我们获取到锁了
                    holdsLock = true;
                    syncPoint.countDown();
                } else {
                    final String nextLowestNode = sortedMembers.get(memberIndex - 1);
                    LOGGER.debug("当前节点：{}，继续等待上一个节点：{}", currentId, nextLowestNode);

                    watchedNode = String.format("%s/%s", lockPath, nextLowestNode);
                    Stat stat = zkClient.exists(watchedNode, this);
                    if (stat == null) {
                        checkForLock();
                    }
                }
            } catch (InterruptedException e) {
                LOGGER.warn("当前节点{}的LockWatcher被interrupted了，取消获取锁请求", currentId, e);
                cancelAttempt();
            } catch (KeeperException e) {
                LOGGER.warn("当前节点{}，出现KeeperException，取消获取锁请求", currentId, e);
                cancelAttempt();
            }
        }

        @Override
        public synchronized void process(WatchedEvent event) {
            // this handles the case where we have aborted a lock and deleted ourselves but still have a
            // watch on the nextLowestNode. This is a workaround since ZK doesn't support unsub.
            // 该判断为了处理这种case：就算我们将自己的锁删除了，但是我们仍然监听着我们之前监听过的节点。因为ZK不支持取消监听
            if (!event.getPath().equals(watchedNode)) {
                LOGGER.info("忽略{}节点的事件", watchedNode);
                return;
            }
            //TODO(Florian Leibert): Pull this into the outer class.
            if (event.getType() == Watcher.Event.EventType.None) {
                switch (event.getState()) {
                    case SyncConnected:
                        // TODO(Florian Leibert): maybe we should just try to "fail-fast" in this case and abort.
                        LOGGER.info("重连中...");
                        break;
                    case Expired:
                        LOGGER.warn("zookeeper session失效了：{}", currentId);
                        cancelAttempt();
                        break;
                }
            } else if (event.getType() == Event.EventType.NodeDeleted) {
                // 如果是我监听的节点的删除事件，则触发我自己获取锁事件
                checkForLock();
            } else {
                LOGGER.warn("不需要的zookeeper事件: {}", event.getType().name());
            }
        }
    }
}
