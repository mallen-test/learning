package org.mallen.test.learning.dl.mysql.optimistic;

import java.sql.*;

import javax.sql.DataSource;

/**
 * 基于mysql的乐观锁实现
 *
 * @author mallen
 * @date 2/24/20
 */
public class OptimisticLock {
    private static final String SQL_QUERY = "select `id`, `resource`, `version` from `optimistic_lock`";
    private static final String STS_UPDATE = "update `optimistic_lock` set `resource` = ?, `version` = ? where id = ? and version = ? ";
    private DataSource dataSource;

    public OptimisticLock(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    /**
     * 查询车票信息
     *
     * @return 查询成功，返回车票信息；否则返回null
     */
    public Tickets query() {
        Connection connection = null;
        Statement statement = null;
        ResultSet rs = null;
        Tickets tickets = null;
        try {
            connection = dataSource.getConnection();
            statement = connection.createStatement();
            rs = statement.executeQuery(SQL_QUERY);
            while (rs.next()) {
                tickets = new Tickets();
                tickets.setId(rs.getLong("id"));
                tickets.setResource(rs.getInt("resource"));
                tickets.setVersion(rs.getInt("version"));
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        } finally {
            if (null != rs) {
                try {
                    rs.close();
                    rs = null;
                } catch (SQLException e) {
                    e.printStackTrace();
                }
            }
            if (null != statement) {
                try {
                    statement.close();
                    statement = null;
                } catch (SQLException e) {
                    e.printStackTrace();
                }
            }
            if (null != connection) {
                try {
                    connection.close();
                    connection = null;
                } catch (SQLException e) {
                    e.printStackTrace();
                }
            }
        }

        return tickets;
    }

    public boolean update(Tickets tickets) {
        Connection connection = null;
        PreparedStatement statement = null;
        try {
            connection = dataSource.getConnection();
            statement = connection.prepareStatement(STS_UPDATE);
            statement.setInt(1, tickets.getResource());
            // 更新版本号
            statement.setInt(2, tickets.getVersion() + 1);
            statement.setLong(3, tickets.getId());
            statement.setInt(4, tickets.getVersion());

            int updateCount = statement.executeUpdate();
            return updateCount < 1 ? false : true;
        } catch (Exception ex) {

        } finally {
            if (null != statement) {
                try {
                    statement.close();
                    statement = null;
                } catch (SQLException e) {
                    e.printStackTrace();
                }
            }
            if (null != connection) {
                try {
                    connection.close();
                    connection = null;
                } catch (SQLException e) {
                    e.printStackTrace();
                }
            }
        }

        return false;
    }


    public static class Tickets {
        private Long id;
        private Integer resource;
        private Integer version;

        public Long getId() {
            return id;
        }

        public void setId(Long id) {
            this.id = id;
        }

        public Integer getResource() {
            return resource;
        }

        public void setResource(Integer resource) {
            this.resource = resource;
        }

        public Integer getVersion() {
            return version;
        }

        public void setVersion(Integer version) {
            this.version = version;
        }
    }
}
