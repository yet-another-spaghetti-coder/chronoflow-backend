CREATE DATABASE IF NOT EXISTS attendee;
USE attendee;

create table IF NOT EXISTS event_attendee
(
    id                     bigint auto_increment comment 'Primary key'
        primary key,
    event_id               bigint                             not null comment 'Event ID',
    user_id                bigint                             null comment 'Attendee user ID',
    check_in_token         varchar(64)                        not null comment 'Unique check-in token',
    check_in_status        tinyint  default 0                 null comment 'Check-in status: 0-Not checked in, 1-Checked in',
    check_in_time          datetime                           null comment 'Check-in time',
    qr_code_generated_time datetime                           null comment 'QR code generated time',
    remark                 varchar(500)                       null,
    tenant_id              bigint                             not null comment 'Tenant ID',
    creator                varchar(64)                        null,
    create_time            datetime default CURRENT_TIMESTAMP not null,
    updater                varchar(64)                        null,
    update_time            datetime default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP,
    deleted                bit      default b'0'              not null,
    attendee_email         varchar(255)                       null comment 'Attendee email',
    attendee_name          varchar(100)                       null comment 'Attendee name',
    attendee_mobile        varchar(20)                        null comment 'Attendee mobile phone',
    constraint uk_event_email
        unique (event_id, attendee_email),
    constraint uk_event_user
        unique (event_id, user_id, deleted),
    constraint uk_token
        unique (check_in_token)
)
    comment 'Event Attendee Table';

create index idx_attendee_email
    on event_attendee (attendee_email);

create index idx_event_id
    on event_attendee (event_id);


