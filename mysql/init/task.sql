CREATE DATABASE IF NOT EXISTS task;
USE task;

create table IF NOT EXISTS task
(
    id          bigint               not null
        primary key,
    user_id     bigint               null,
    event_id    bigint               null,
    name        varchar(100)         null,
    description varchar(255)         null,
    status      tinyint(1) default 0 not null comment '0 - Enable; 1 - Disable',
    remark      varchar(200)         null,
    start_time  datetime             null,
    end_time    datetime             null,
    creator     varchar(100)         null,
    create_time datetime             null,
    updater     varchar(100)         null,
    update_time datetime             null,
    deleted     tinyint(1) default 0 not null comment '0 - Normal; 1- Deleted',
    tenant_id   bigint               null
);

create table IF NOT EXISTS task_log
(
    id             bigint               not null
        primary key,
    task_id        bigint               null,
    action         int                  null comment 'Operation actions
CREATE
UPDATE
PAUSE
CANCEL
COMPLETE
ASSIGN',
    target_user_id bigint               null,
    money_cost     decimal(10, 2)       null comment 'Monetary cost (amount, unit: yuan/US dollars, etc., in the system''s unified currency)',
    labor_hour     decimal(10, 2)       null comment 'Manpower input hours (hours, can be decimal, such as 1.5 hours)',
    remark         varchar(200)         null,
    creator        varchar(100)         null,
    create_time    datetime             null,
    updater        varchar(100)         null,
    update_time    datetime             null,
    deleted        tinyint(1) default 0 not null comment '0 - Normal; 1- Deleted',
    tenant_id      bigint               null
);


