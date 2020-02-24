CREATE TABLE optimistic_lock
(
    `id`       BIGINT NOT NULL AUTO_INCREMENT,
    `resource` int    NOT NULL COMMENT '锁定的标识',
    `version`  int    NOT NULL COMMENT '加锁时间，毫秒级别时间戳',
    PRIMARY KEY (`id`)
) ENGINE = InnoDB COMMENT ='数据库分布式锁表';

insert into optimistic_lock(`resource`, `version`) value (100, 1);