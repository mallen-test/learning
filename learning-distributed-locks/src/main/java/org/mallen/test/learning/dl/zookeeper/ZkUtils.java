package org.mallen.test.learning.dl.zookeeper;

import org.apache.commons.lang3.StringUtils;
import org.apache.zookeeper.CreateMode;
import org.apache.zookeeper.KeeperException;
import org.apache.zookeeper.ZooKeeper;
import org.apache.zookeeper.data.ACL;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * @author mallen
 * @date 2/25/20
 */
public class ZkUtils {
    private static final Logger LOGGER = LoggerFactory.getLogger(ZkUtils.class);
    /**
     * 检查path的合法性，如果path不存在，则创建path
     *
     * @param zkClient
     * @param acl
     * @param path
     */
    public static void ensurePath(ZooKeeper zkClient, List<ACL> acl, String path) throws KeeperException, InterruptedException {
        if (StringUtils.isBlank(path)) {
            throw new LockingException("path can't be blank");
        }
        if (!path.startsWith("/")) {
            throw new LockingException("path must start with /");
        }
        doEnsurePath(zkClient, acl, path);
    }

    public static void doEnsurePath(ZooKeeper zkClient, List<ACL> acl, String path) throws KeeperException, InterruptedException {
        if (zkClient.exists(path, false) == null) {
            // 当前节点不存在，则向上层追溯并确保上层节点存在，除非单前已经是root节点了
            int lastPathIndex = path.lastIndexOf('/');
            if (lastPathIndex > 0) {
                doEnsurePath(zkClient, acl, path.substring(0, lastPathIndex));
            }

            // 已经确认上层节点存在，所以开始创建当前节点
            try {
                zkClient.create(path, null, acl, CreateMode.PERSISTENT);
            } catch (KeeperException.NodeExistsException e) {
                // 在创建将节点的时候，如果另外一个线程与我们产生竞争，并且另外一个线程创建成功了，那么我们的创建操作将会抛出NodeExistsException
                LOGGER.debug("某个线程已经创建了路径：{}", path);
            }
        }
    }
}
