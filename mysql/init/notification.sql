CREATE DATABASE IF NOT EXISTS notification;
USE notification;

create table notification_delivery
(
    id            varchar(36)                                                                       not null
        primary key,
    created_at    datetime(6)                                                                       not null,
    updated_at    datetime(6)                                                                       null,
    channel       enum ('EMAIL', 'PUSH', 'WS')                                                      not null,
    event_id      varchar(120)                                                                      not null,
    recipient_key varchar(160)                                                                      not null,
    status        enum ('CREATED', 'DELIVERED', 'FAILED')                                           not null,
    type          enum ('ATTENDEE_INVITE', 'MEMBER_INVITE', 'NEW_TASK_ASSIGN', 'ORGANIZER_WELCOME') not null,
    constraint uk_event_channel_recipient
        unique (event_id, channel, recipient_key)
);

create table email_message
(
    delivery_id         varchar(36)                        not null
        primary key,
    created_at          datetime(6)                        not null,
    updated_at          datetime(6)                        null,
    error_message       tinytext                           null,
    provider            enum ('AWS_SES')                   not null,
    provider_message_id varchar(200)                       null,
    status              enum ('FAILED', 'PENDING', 'SENT') not null,
    constraint fk_email_delivery
        foreign key (delivery_id) references notification_delivery (id)
);

create index idx_recipient_created
    on notification_delivery (recipient_key asc, created_at desc);

create table notification_device
(
    id         varchar(36)                           not null
        primary key,
    created_at datetime(6)                           not null,
    updated_at datetime(6)                           null,
    platform   enum ('ANDROID', 'IOS', 'WEB')        not null,
    status     enum ('ACTIVE', 'INVALID', 'REVOKED') not null,
    token      varchar(1024)                         not null,
    user_id    varchar(64)                           not null
);

create index idx_device_user_status
    on notification_device (user_id, status);

create table push_message
(
    delivery_id   varchar(36)                        not null
        primary key,
    created_at    datetime(6)                        not null,
    updated_at    datetime(6)                        null,
    error_message longtext                           null,
    fcm_id        varchar(200)                       null,
    status        enum ('FAILED', 'PENDING', 'SENT') not null,
    token         varchar(512)                       null,
    constraint fk_push_delivery
        foreign key (delivery_id) references notification_delivery (id)
);


