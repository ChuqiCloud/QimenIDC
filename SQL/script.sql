create table config
(
    id                     int                      not null
        primary key,
    token                  varchar(255) default '0' not null,
    linux_system_disk_size int                      null,
    win_system_disk_size   int                      null
);

create table ippool
(
    id          int auto_increment
        primary key,
    node_id     int           not null,
    vm_id       int           null,
    pool_id     int           null,
    ip          varchar(15)   not null,
    subnet_mask varchar(15)   not null,
    gateway     varchar(15)   not null,
    dns1        varchar(15)   null,
    dns2        varchar(15)   null,
    status      int default 0 not null
);

create table ipstatus
(
    id        int auto_increment
        primary key,
    name      varchar(255) not null,
    gateway   varchar(15)  null,
    mask      int          null,
    dns1      varchar(15)  null,
    dns2      varchar(15)  null,
    available int          null comment '可用',
    used      int          null comment '已用',
    disable   int          null comment '禁用',
    nodeId    int          null
);

create table master
(
    id                int auto_increment
        primary key,
    host              varchar(255)               not null,
    port              int          default 8006  not null,
    username          varchar(255)               not null,
    password          varchar(255)               not null,
    realm             varchar(50)  default 'pam' not null,
    status            int          default 0     not null comment '0正常1停止',
    csrf_token        varchar(255)               null,
    ticket            text                       null,
    node_name         varchar(255) default 'pve' not null,
    auto_storage      varchar(255)               null,
    ssh_port          int                        null,
    ssh_username      varchar(255)               null,
    ssh_password      varchar(255)               null,
    controller_status int                        null
);

create table os
(
    id          int auto_increment
        primary key,
    name        varchar(255)  not null,
    file_name   varchar(255)  null comment '文件全名',
    type        varchar(255)  null comment '镜像类型',
    arch        varchar(255)  null comment '镜像架构',
    os_type     varchar(255)  null comment '镜像操作系统名',
    node_status json          null,
    down_type   int default 0 not null comment '0=url下载;1=手动上传',
    url         varchar(255)  null,
    size        mediumtext    null,
    path        varchar(255)  null,
    cloud       int default 0 null comment 'cloud-init(0=未开启 1=开启)',
    status      int           null,
    create_time mediumtext    null
);

create table sysapi
(
    id          int auto_increment
        primary key,
    appid       varchar(255) not null,
    appkey      varchar(255) not null,
    info        varchar(255) null comment '备注',
    create_date mediumtext   null
);

create table sysuser
(
    id        int auto_increment
        primary key,
    uuid      varchar(255) not null,
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
    ip_config        json                                       null,
    nested           int          default 0                     not null comment '嵌套虚拟化',
    task             json                                       null comment '任务流',
    status           int          default 0                     not null,
    create_time      mediumtext                                 not null comment '创建时间',
    expiration_time  mediumtext                                 not null comment '到期时间'
);


