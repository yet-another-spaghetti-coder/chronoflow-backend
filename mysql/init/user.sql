CREATE DATABASE IF NOT EXISTS user;
USE user;

create table IF NOT EXISTS sys_dict_data
(
    id          int                  not null
        primary key,
    dict_type   varchar(100)         null,
    sort        int                  null comment 'Display order (the smaller the value, the higher the priority)',
    label       varchar(100)         null comment 'Text displayed to the user (such as "Enable", "Disable")',
    value       varchar(100)         null comment 'The actual stored value (such as "0", "1" or English code)',
    status      tinyint(1) default 0 not null comment '0 - Enable; 1 - Disable',
    remark      varchar(200)         null,
    creator     varchar(100)         null,
    create_time datetime             null,
    updater     varchar(100)         null,
    update_time datetime             null,
    deleted     tinyint(1) default 0 not null comment '0 - Normal; 1- Deleted'
);

create table IF NOT EXISTS sys_dict_type
(
    id          bigint               not null
        primary key,
    name        varchar(100)         null,
    type        int                  null,
    status      tinyint(1) default 0 not null comment '0 - Enable; 1 - Disable',
    remark      varchar(200)         null,
    creator     varchar(100)         null,
    create_time datetime             null,
    updater     varchar(100)         null,
    update_time datetime             null,
    deleted     tinyint(1) default 0 not null comment '0 - Normal; 1- Deleted'
);

create table IF NOT EXISTS sys_permission
(
    id             bigint                       not null
        primary key,
    name           varchar(100)                 null,
    permission_key varchar(50)                  null,
    description    varchar(100)                 null,
    type           tinyint                      null comment '1-menu 2-button 3-API',
    parent_id      bigint                       null comment 'To store parent permission id',
    status         tinyint    default 0         null comment '0-Enable 1- Disable',
    creator        varchar(100)                 null,
    create_time    datetime                     null,
    updater        varchar(100) charset utf8mb3 null,
    update_time    datetime                     null,
    deleted        tinyint(1) default 0         null,
    tenant_id      bigint                       null
);

create table IF NOT EXISTS sys_post
(
    id          bigint               not null
        primary key,
    name        varchar(100)         null,
    sort        int                  null comment 'Display order (the smaller the value, the higher the priority)',
    status      tinyint(1) default 0 not null comment '0 - Enable; 1 - Disable',
    remark      varchar(200)         null,
    creator     varchar(100)         null,
    create_time datetime             null,
    updater     varchar(100)         null,
    update_time datetime             null,
    deleted     tinyint(1) default 0 not null comment '0 - Normal; 1- Deleted',
    tenant_id   bigint               null
)
    comment 'Position info table';

create table IF NOT EXISTS sys_role
(
    id              bigint               not null
        primary key,
    name            varchar(100)         null,
    role_key        varchar(50)          null,
    status          tinyint(1) default 0 not null comment '0 - Enable; 1 - Disable',
    permission_list json                 null,
    remark          varchar(200)         null,
    creator         varchar(100)         null,
    create_time     datetime             null,
    updater         varchar(100)         null,
    update_time     datetime             null,
    deleted         tinyint(1) default 0 not null comment '0 - Normal; 1- Deleted',
    tenant_id       bigint               null
);

create table IF NOT EXISTS sys_role_permission
(
    id            bigint                       not null
        primary key,
    role_id       bigint                       null,
    permission_id bigint                       null,
    creator       varchar(100)                 null,
    create_time   datetime                     null,
    updater       varchar(100) charset utf8mb3 null,
    update_time   datetime                     null,
    deleted       tinyint(1) default 0         null,
    tenant_id     bigint                       null
);

create table IF NOT EXISTS sys_tenant
(
    id              bigint               not null comment 'Primary key'
        primary key,
    name            varchar(100)         null comment 'Organization name',
    contact_user_id bigint               null comment 'Store sys_user table primary key',
    contact_name    varchar(100)         null comment 'Contact person''s name',
    contact_mobile  varchar(20)          null comment 'Contact person''s mobile',
    address         varchar(500)         null,
    status          tinyint(1) default 0 not null comment '0 - Enable; 1 - Disable',
    tenant_code     varchar(20)          null comment 'User can use this code to join organization',
    creator         varchar(100)         null,
    create_time     datetime             null,
    updater         varchar(100)         null,
    update_time     datetime             null,
    deleted         tinyint(1) default 0 not null comment '0 - Normal; 1 - Deleted'
)
    comment 'Tenant (Organization) table';

create table IF NOT EXISTS sys_user
(
    id          bigint auto_increment comment 'Primary Key'
        primary key,
    username    varchar(100)                 null,
    password    varchar(100)                 null,
    remark      varchar(200)                 null,
    email       varchar(200)                 null,
    phone       varchar(20)                  null,
    status      tinyint(1) default 0         not null comment '0 - Enable; 1 - Disable',
    login_time  datetime                     null comment 'The latest login datetime',
    dept_id     bigint                       null,
    post_list   json                         null,
    creator     varchar(100) charset utf8mb3 null,
    create_time datetime                     null,
    updater     varchar(100) charset utf8mb3 null,
    update_time datetime                     null,
    deleted     tinyint(1) default 0         not null comment '0 - Normal; 1- Deleted',
    tenant_id   bigint                       null,
    constraint username_unique_key
        unique (username)
)
    comment 'User info table';

create table IF NOT EXISTS sys_user_post
(
    id          bigint               not null
        primary key,
    user_id     bigint               null,
    post_id     bigint               null,
    creator     varchar(100)         null,
    create_time datetime             null,
    updater     varchar(100)         null,
    update_time datetime             null,
    deleted     tinyint(1) default 0 not null comment '0 - Normal; 1- Deleted',
    tenant_id   bigint               null
);

create table IF NOT EXISTS sys_user_role
(
    id          bigint                       not null
        primary key,
    user_id     bigint                       null,
    role_id     bigint                       null,
    creator     varchar(100)                 null,
    create_time datetime                     null,
    updater     varchar(100) charset utf8mb3 null,
    update_time datetime                     null,
    deleted     tinyint(1) default 0         null,
    tenant_id   bigint                       null
);

# Permissions
INSERT INTO user.sys_permission (id, name, permission_key, description, type, parent_id, status, creator, create_time, updater, update_time, deleted, tenant_id) VALUES (1971465366969307138, 'All permission', '*', null, 3, null, 0, null, null, null, null, 0, null);
INSERT INTO user.sys_permission (id, name, permission_key, description, type, parent_id, status, creator, create_time, updater, update_time, deleted, tenant_id) VALUES (1971465366969307139, 'Create member', 'system:organizer:member:create', null, 3, null, 0, null, null, null, null, 0, null);
INSERT INTO user.sys_permission (id, name, permission_key, description, type, parent_id, status, creator, create_time, updater, update_time, deleted, tenant_id) VALUES (1971465366969307140, 'Update member', 'system:organizer:member:update', null, 3, null, 0, null, null, null, null, 0, null);
INSERT INTO user.sys_permission (id, name, permission_key, description, type, parent_id, status, creator, create_time, updater, update_time, deleted, tenant_id) VALUES (1971465366969307141, 'Delete member', 'system:organizer:member:delete', null, 3, null, 0, null, null, null, null, 0, null);
INSERT INTO user.sys_permission (id, name, permission_key, description, type, parent_id, status, creator, create_time, updater, update_time, deleted, tenant_id) VALUES (1971465366969307142, 'Restore member', 'system:organizer:member:restore', null, 3, null, 0, null, null, null, null, 0, null);
INSERT INTO user.sys_permission (id, name, permission_key, description, type, parent_id, status, creator, create_time, updater, update_time, deleted, tenant_id) VALUES (1971465366969307143, 'Disable member', 'system:organizer:member:disable', null, 3, null, 0, null, null, null, null, 0, null);
INSERT INTO user.sys_permission (id, name, permission_key, description, type, parent_id, status, creator, create_time, updater, update_time, deleted, tenant_id) VALUES (1971465366969307144, 'Enable member', 'system:organizer:member:enable', null, 3, null, 0, null, null, null, null, 0, null);
INSERT INTO user.sys_permission (id, name, permission_key, description, type, parent_id, status, creator, create_time, updater, update_time, deleted, tenant_id) VALUES (1971465366969307145, 'Query member', 'system:organizer:member:query', null, 3, null, 0, null, null, null, null, 0, null);
INSERT INTO user.sys_permission (id, name, permission_key, description, type, parent_id, status, creator, create_time, updater, update_time, deleted, tenant_id) VALUES (1971465366969307147, 'All system permission', 'system:*', null, 3, null, 0, null, null, null, null, 0, null);
INSERT INTO user.sys_permission (id, name, permission_key, description, type, parent_id, status, creator, create_time, updater, update_time, deleted, tenant_id) VALUES (1972530048253644801, 'Create role', 'system:role:role:create', '', 3, null, 0, '1', '2025-09-29 13:13:06', '1', '2025-09-29 13:13:06', 0, null);
INSERT INTO user.sys_permission (id, name, permission_key, description, type, parent_id, status, creator, create_time, updater, update_time, deleted, tenant_id) VALUES (1972530312687734786, 'Update role', 'system:role:role:update', '', 3, null, 0, '1', '2025-09-29 13:14:09', '1', '2025-09-29 13:14:09', 0, null);
INSERT INTO user.sys_permission (id, name, permission_key, description, type, parent_id, status, creator, create_time, updater, update_time, deleted, tenant_id) VALUES (1972530368878825473, 'Delete role', 'system:role:role:delete', '', 3, null, 0, '1', '2025-09-29 13:14:22', '1', '2025-09-29 13:14:22', 0, null);
INSERT INTO user.sys_permission (id, name, permission_key, description, type, parent_id, status, creator, create_time, updater, update_time, deleted, tenant_id) VALUES (1972530416639365122, 'Query role', 'system:role:role:query', '', 3, null, 0, '1', '2025-09-29 13:14:34', '1', '2025-09-29 13:14:34', 0, null);
INSERT INTO user.sys_permission (id, name, permission_key, description, type, parent_id, status, creator, create_time, updater, update_time, deleted, tenant_id) VALUES (1972530460977352706, 'Assign role', 'system:role:role:assign', '', 3, null, 0, '1', '2025-09-29 13:14:44', '1', '2025-09-29 13:14:44', 0, null);
INSERT INTO user.sys_permission (id, name, permission_key, description, type, parent_id, status, creator, create_time, updater, update_time, deleted, tenant_id) VALUES (1972530560973754370, 'Create event', 'system:event:event:create', '', 3, null, 0, '1', '2025-09-29 13:15:08', '1', '2025-09-29 13:15:08', 0, null);
INSERT INTO user.sys_permission (id, name, permission_key, description, type, parent_id, status, creator, create_time, updater, update_time, deleted, tenant_id) VALUES (1972530615646507010, 'Update event', 'system:event:event:update', '', 3, null, 0, '1', '2025-09-29 13:15:21', '1', '2025-09-29 13:15:21', 0, null);
INSERT INTO user.sys_permission (id, name, permission_key, description, type, parent_id, status, creator, create_time, updater, update_time, deleted, tenant_id) VALUES (1972530679114715138, 'Delete event', 'system:event:event:delete', '', 3, null, 0, '1', '2025-09-29 13:15:36', '1', '2025-09-29 13:15:36', 0, null);
INSERT INTO user.sys_permission (id, name, permission_key, description, type, parent_id, status, creator, create_time, updater, update_time, deleted, tenant_id) VALUES (1972530723331067905, 'Query event', 'system:event:event:query', '', 3, null, 0, '1', '2025-09-29 13:15:47', '1', '2025-09-29 13:15:47', 0, null);
INSERT INTO user.sys_permission (id, name, permission_key, description, type, parent_id, status, creator, create_time, updater, update_time, deleted, tenant_id) VALUES (1972530831342784513, 'Create task', 'system:event:task:create', '', 3, null, 0, '1', '2025-09-29 13:16:13', '1', '2025-09-29 13:16:13', 0, null);
INSERT INTO user.sys_permission (id, name, permission_key, description, type, parent_id, status, creator, create_time, updater, update_time, deleted, tenant_id) VALUES (1972530892776755202, 'Update task', 'system:event:task:update', '', 3, null, 0, '1', '2025-09-29 13:16:27', '1', '2025-09-29 13:16:27', 0, null);
INSERT INTO user.sys_permission (id, name, permission_key, description, type, parent_id, status, creator, create_time, updater, update_time, deleted, tenant_id) VALUES (1972530937144102913, 'Delete task', 'system:event:task:delete', '', 3, null, 0, '1', '2025-09-29 13:16:38', '1', '2025-09-29 13:16:38', 0, null);
INSERT INTO user.sys_permission (id, name, permission_key, description, type, parent_id, status, creator, create_time, updater, update_time, deleted, tenant_id) VALUES (1972530991993016322, 'Query task', 'system:event:task:query', '', 3, null, 0, '1', '2025-09-29 13:16:51', '1', '2025-09-29 13:16:51', 0, null);
INSERT INTO user.sys_permission (id, name, permission_key, description, type, parent_id, status, creator, create_time, updater, update_time, deleted, tenant_id) VALUES (1972531040659525634, 'Assign task', 'system:event:task:assign', '', 3, null, 0, '1', '2025-09-29 13:17:03', '1', '2025-09-29 13:17:03', 0, null);
