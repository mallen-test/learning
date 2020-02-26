CREATE TABLE `database_lock`
(
    `id`          BIGINT      NOT NULL AUTO_INCREMENT,
    `lock`        int         NOT NULL COMMENT '锁的标识',
    `lock_at`     BIGINT      NOT NULL COMMENT '加锁时间，毫秒级别时间戳',
    `locker`      varchar(50) NOT NULL COMMENT '加锁线程标识',
    `description` varchar(1024) COMMENT '描述',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uiq_idx_lock` (`lock`)
) ENGINE = InnoDB COMMENT ='数据库分布式锁表';