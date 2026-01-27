CREATE DATABASE IF NOT EXISTS file;
USE file;

create table IF NOT EXISTS file
(
    id          bigint               not null
        primary key,
    task_log_id bigint               null,
    event_id    bigint               null,
    name        varchar(100)         null,
    object_name varchar(255)         null,
    provider    varchar(50)          null,
    type        varchar(255)         null comment 'Save only file extensions (pdf, jpg, mp4)',
    size        bigint unsigned      null,
    creator     varchar(100)         null,
    create_time datetime             null,
    updater     varchar(100)         null,
    update_time datetime             null,
    deleted     tinyint(1) default 0 not null comment '0 - Normal; 1- Deleted',
    tenant_id   bigint               null
);


