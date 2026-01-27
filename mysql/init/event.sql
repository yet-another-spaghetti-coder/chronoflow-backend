CREATE DATABASE IF NOT EXISTS event;
USE event;

create table IF NOT EXISTS event
(
    id          bigint               not null
        primary key,
    user_id     bigint               null,
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
    tenant_id   bigint               null,
    location    varchar(255)         null
);

create table IF NOT EXISTS event_participant
(
    id          bigint auto_increment
        primary key,
    event_id    bigint            not null,
    user_id     bigint            not null,
    tenant_id   bigint            not null,
    create_time datetime          not null,
    update_time datetime          not null,
    deleted     tinyint default 0 null,
    creator     bigint            null,
    updater     bigint            null,
    constraint uk_event_user
        unique (event_id, user_id, tenant_id)
);

create table IF NOT EXISTS sys_dept
(
    id           bigint               not null
        primary key,
    name         varchar(100)         null,
    sort         int                  null comment 'Display order (the smaller the value, the higher the priority)',
    lead_user_id bigint               null,
    remark       varchar(200)         null,
    status       tinyint(1) default 0 not null comment '0 - Enable; 1 - Disable',
    creator      varchar(100)         null,
    create_time  datetime             null,
    updater      varchar(100)         null,
    update_time  datetime             null,
    deleted      tinyint(1) default 0 not null comment '0 - Normal; 1- Deleted',
    tenant_id    bigint               null,
    event_id     bigint               null
)
    comment 'Group info table';

create table IF NOT EXISTS sys_user_group
(
    id          bigint auto_increment
        primary key,
    user_id     bigint                                not null comment 'User ID',
    dept_id     bigint                                not null comment 'Group ID',
    event_id    bigint                                not null comment 'Event ID',
    join_time   datetime    default CURRENT_TIMESTAMP null comment 'jion time',
    role_type   int                                   null comment 'role type',
    creator     varchar(64) default ''                null comment 'creator',
    create_time datetime    default CURRENT_TIMESTAMP not null comment 'create time',
    updater     varchar(64) default ''                null comment 'updater',
    update_time datetime    default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP,
    deleted     bit         default b'0'              not null comment 'delete or not',
    tenant_id   bigint      default 0                 not null comment 'tenant id',
    constraint uk_user_event
        unique (user_id, event_id, deleted) comment 'One user in one event only can be in one group'
)
    comment 'User-Group Associated table';

create index idx_dept_id
    on sys_user_group (dept_id);

create index idx_event_id
    on sys_user_group (event_id);


