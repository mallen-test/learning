CREATE TABLE optimistic_lock
(
    `id`       BIGINT NOT NULL AUTO_INCREMENT,
    `resource` int    NOT NULL COMMENT '资源数量',
    `version`  int    NOT NULL COMMENT '版本',
    PRIMARY KEY (`id`)
) ENGINE = InnoDB COMMENT ='数据库分布式锁表';

insert into optimistic_lock(`resource`, `version`) value (100, 1);