create table master
(
    id           int auto_increment
        primary key,
    host         varchar(255)               not null,
    port         int          default 8006  not null,
    username     varchar(255)               not null,
    password     varchar(255)               not null,
    realm        varchar(50)  default 'pam' not null,
    status       int          default 0     not null comment '0正常1停止',
    csrf_token   varchar(255)               null,
    ticket       text                       null,
    node_name    varchar(255) default 'pve' not null,
    auto_storage varchar(255)               null,
    ssh_port     int                        null,
    ssh_username varchar(255)               null,
    ssh_password varchar(255)               null
);

create table sysapi
(
    id     int auto_increment
        primary key,
    appid  varchar(255) not null,
    appkey varchar(255) not null
);

create table sysuser
(
    id        int auto_increment
        primary key,
    username  varchar(255) not null,
    password  varchar(255) not null,
    name      varchar(255) null,
    phone     varchar(20)  null,
    email     varchar(255) null,
    logindate varchar(255) null
);

create table task
(
    id          int auto_increment
        primary key,
    nodeid      int         null,
    vmid        int         null,
    hostid      int         null,
    type        int         null,
    status      int         null,
    params      json        null,
    error       text        null,
    create_date varchar(13) null
);

create table vmhost
(
    id               int auto_increment
        primary key,
    nodeid           int                                        not null,
    vmid             int                                        null,
    name             varchar(255)                               not null,
    cores            int                                        not null,
    memory           int                                        not null,
    agent            int          default 1                     not null,
    ide0             varchar(255)                               null,
    ide2             varchar(255) default 'local-lvm:cloudinit' null comment 'cloud-init',
    net0             varchar(255) default 'virtio,bridge=vmbr0' null,
    net1             varchar(255)                               null,
    os               varchar(255)                               null,
    bandwidth        int                                        null,
    storage          varchar(255)                               null,
    system_disk_size int                                        null,
    data_disk        json                                       null,
    bridge           varchar(255)                               null,
    task             json                                       null comment '任务流'
);


