package org.mallen.test.learning.dl.mysql.table;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.UUID;

/**
 * 使用mysql的表，实现分布式锁
 *
 * @author mallen
 * @date 2/21/20
 */
public class MysqlTableLock {
    private static final String STS_LOCK_SQL = "insert into `database_lock`(`lock`, `lock_at`, `locker`,`description`) value(?, ?, ?, ?)";
    private static final String STS_UNLOCK_SQL = "delete from `database_lock` where `lock` = ? and `locker` = ?";
    private DataSource dataSource;
    private int lockId;
    private String desc;
    private String id;

    public MysqlTableLock(DataSource dataSource, int lockId, String desc) {
        this.dataSource = dataSource;
        this.lockId = lockId;
        this.desc = desc;
        this.id = UUID.randomUUID().toString();
    }

    public MysqlTableLock(DataSource dataSource, int lockId) {
        this.dataSource = dataSource;
        this.lockId = lockId;
        this.id = UUID.randomUUID().toString();
    }

    public Boolean tryLock() {
        Connection connection = null;
        PreparedStatement preparedStatement = null;
        try {
            connection = dataSource.getConnection();
            preparedStatement = connection.prepareStatement(STS_LOCK_SQL);
            preparedStatement.setInt(1, lockId);
            preparedStatement.setLong(2, System.currentTimeMillis());
            preparedStatement.setString(3, getLocker());
            preparedStatement.setString(4, desc);

            preparedStatement.execute();
            // 只要不抛错，则说明获取锁成功
            return Boolean.TRUE;
        } catch (Exception ex) {
        } finally {
            if (null != preparedStatement) {
                try {
                    preparedStatement.close();
                    preparedStatement = null;
                } catch (SQLException e) {
                    e.printStackTrace();
                }
            }
            if (null != connection) {
                try {
                    connection.close();
                    connection = null;
                } catch (SQLException e) {
                    System.out.println("关闭连接失败");
                }
            }
        }

        return Boolean.FALSE;
    }

    public void unlock() {
        Connection connection = null;
        PreparedStatement preparedStatement = null;
        try {
            connection = dataSource.getConnection();
            preparedStatement = connection.prepareStatement(STS_UNLOCK_SQL);
            preparedStatement.setInt(1, lockId);
            preparedStatement.setString(2, getLocker());
            preparedStatement.execute();
        } catch (Exception ex) {
            ex.printStackTrace();
        } finally {
            if (null != preparedStatement) {
                try {
                    preparedStatement.close();
                    preparedStatement = null;
                } catch (SQLException e) {
                    e.printStackTrace();
                }
            }
            if (null != connection) {
                try {
                    connection.close();
                } catch (SQLException e) {
                    System.out.println("关闭连接失败");
                }
            }
        }
    }

    private String getLocker() {
        return id + ":" + Thread.currentThread().getId();
    }
}
