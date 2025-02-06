create table area
(
    id     int auto_increment
        primary key,
    name   varchar(255)  not null,
    parent int           null comment '父级',
    realm  int default 0 not null
);

create table config
(
    id                     int                         not null
        primary key,
    token                  varchar(255) default '0'    not null,
    linux_system_disk_size int          default 40     null,
    win_system_disk_size   int          default 60     null,
    bwlimit                bigint       default 512000 null comment '默认io限制',
    vnc_time               int          default 120    null comment 'vnc失效时间，单位分钟',
    version                varchar(255)                null,
    build                  varchar(255)                null,
    installed              int          default 1      not null comment '0=否;1=是'
);

create table configuretemplate
(
    id               int auto_increment
        primary key,
    name             varchar(255)                null,
    cores            int                         null comment '核数',
    sockets          int                         null,
    threads          int                         null,
    devirtualization int                         null,
    kvm              int                         null,
    cpu_model        int                         null,
    model_group      int                         null,
    nested           int                         null,
    cpu              varchar(255)                null,
    cpu_units        int                         null,
    bwlimit          bigint                      null,
    arch             varchar(255)                null,
    acpi             int          default 1      null,
    memory           int          default 512    null,
    storage          varchar(255) default 'auto' null,
    system_disk_size int                         null,
    data_disk        json                        null,
    bandwidth        int                         null,
    on_boot          int          default 0      null
)
    comment '配置模板';

create table cpuinfo
(
    id          int auto_increment
        primary key,
    cpu         varchar(255) null,
    name        varchar(255) null,
    family      int          null,
    model       int          null,
    stepping    int          null,
    level       varchar(255) null,
    xlevel      varchar(255) null comment 'cpu拓展型号',
    vendor      varchar(255) null comment '厂商',
    l3_cache    int          null comment '三缓',
    other       text         null,
    create_date varchar(13)  null
);

create table flowdata
(
    id          int auto_increment
        primary key,
    node_id     int              null,
    hostid      int              null,
    rrd         json             null,
    used_flow   double default 0 not null comment '已用流量',
    status      int    default 0 not null comment '0=未同步;1=已同步',
    create_date varchar(20)      null
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
    mac         varchar(255)  null,
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
create table ipforward
(
    id          int auto_increment
        primary key,
    node_id     int           not null,
    vm_id       int           null,
    port          varchar(15)   not null,
    vm_ip varchar(15)   not null,
    vm_port     varchar(15)   not null,
    status      int default 0 not null
);
create table master
(
    id                int auto_increment
        primary key,
    name              varchar(255)               null,
    area              int                        null,
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
    controller_status int                        null,
    controller_port   int          default 7600  null comment '被控端口',
    naton             int          default 0     not null comment '0关闭 1开启',
    natbridge             varchar(50)  null,
    natippool            int          default 0     not null comment 'nat ip池id',
    nataddr             varchar(50)  null comment 'nat展示地址'
);

create table modelgroup
(
    id           int auto_increment
        primary key,
    cpu_model    int          null,
    smbios_model varchar(255) null,
    args         text         null,
    info         text         null,
    create_date  varchar(13)  null
);

create table natcontroller
(
    id         int auto_increment
        primary key,
    name       varchar(255)  null,
    host       varchar(255)  null,
    port       int           null,
    port_range varchar(255)  null,
    status     int default 0 not null
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
    reason      text          null comment '异常原因',
    create_time mediumtext    null
);

create table smbios
(
    id          int auto_increment
        primary key,
    type        int         null,
    model       json        null,
    info        text        null,
    create_date varchar(13) null
);

create table subnet
(
    id        int auto_increment
        primary key,
    nodeid    int                           null,
    subnet    varchar(255)                  null,
    type      varchar(255) default 'subnet' not null,
    vnet      varchar(255)                  not null,
    gateway   varchar(255)                  null,
    mask      int                           null,
    dns       varchar(255)                  null,
    snat      int          default 0        not null,
    available int                           null,
    used      int                           null,
    disable   int                           null,
    state     varchar(255) default 'new'    not null
);

create table subnetpool
(
    id        int auto_increment
        primary key,
    node_id   int           null,
    vm_id     int           null,
    subnat_id int           null,
    ip        varchar(255)  null,
    mask      int           null,
    gateway   varchar(15)   null,
    mac       varchar(255)  null,
    dns       varchar(15)   null,
    status    int default 0 not null
);

create table sysapi
(
    id          int auto_increment
        primary key,
    appid       varchar(255) not null,
    appkey      varchar(255) not null,
    info        varchar(255) null comment '备注',
    status      int          null comment '0=正常，1=停用',
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
    id                    int auto_increment
        primary key,
    nodeid                int                                        not null,
    vmid                  int                                        null,
    hostname              varchar(255)                               not null,
    configure_template_id int                                        null,
    sockets               int                                        null,
    cores                 int                                        not null,
    threads               int                                        null,
    devirtualization      int                                        null,
    kvm                   int                                        null,
    cpu_model             int                                        null,
    model_group           int                                        null,
    cpu                   varchar(255)                               null,
    cpu_units             int                                        null,
    bwlimit               bigint                                     null,
    flow_limit            bigint       default 0                     not null comment '月流量上限',
    used_flow             double       default 0                     not null comment '已用流量',
    last_reset_flow       bigint       default 0                     not null comment '上次重置流量月份',
    args                  text                                       null,
    arch                  varchar(20)                                null,
    acpi                  int                                        null,
    memory                int                                        not null,
    swap                  int                                        null,
    agent                 int          default 1                     not null,
    username              varchar(255)                               null,
    password              varchar(255)                               null,
    ide0                  varchar(255)                               null,
    ide2                  varchar(255) default 'local-lvm:cloudinit' null comment 'cloud-init',
    net0                  varchar(255) default 'virtio,bridge=vmbr0' null,
    net1                  varchar(255)                               null,
    os                    varchar(255)                               null,
    os_name               varchar(255)                               null,
    os_type               varchar(20)                                null,
    iso                   varchar(255)                               null,
    template              varchar(255)                               null,
    on_boot               int                                        null,
    bandwidth             int                                        null,
    storage               varchar(255)                               null,
    system_disk_size      int                                        null,
    mbps_rd               int          default 0                     not null comment '读取长效限制 单位mb/s',
    mbps_rd_max           int          default 0                     not null comment '读取突发限制 单位mb/s',
    mbps_wr               int          default 0                     not null comment '写长效限制 单位mb/s',
    mbps_wr_max           int          default 0                     not null comment '写突发限制 单位mb/s',
    iops_rd               int          default 0                     not null comment 'iops读长效限制 单位ops/s',
    iops_rd_max           int          default 0                     not null comment 'iops读突发限制 单位ops/s',
    iops_wr               int          default 0                     not null comment 'iops写长效限制 单位ops/s',
    iops_wr_max           int          default 0                     not null comment 'iops写突发限制 单位ops/s',
    data_disk             json                                       null,
    bridge                varchar(255)                               null,
    ip_config             json                                       null,
    ip_data               text                                       null,
    nested                int          default 0                     not null comment '嵌套虚拟化',
    task                  json                                       null comment '任务流',
    status                int          default 0                     not null,
    pause_info            text                                       null comment '暂停原因',
    create_time           mediumtext                                 not null comment '创建时间',
    expiration_time       mediumtext                                 not null comment '到期时间',
    ip_list               text                                       null,
    ifnat                 int          default 0                     not null comment '是否nat 0否 1是',
    natnum                int          default 0                     not null comment 'nat端口转发数量'
);

create table vncdata
(
    id              int auto_increment
        primary key,
    vnc_id          bigint        null,
    host_id         int           null,
    node_id         int           null,
    port            int           null,
    status          int default 0 not null comment '0=正常；1=失效',
    create_date     bigint        null,
    expiration_time bigint        null comment '失效时间'
);

create table vncinfo
(
    id       int auto_increment
        primary key,
    host_id  bigint       null,
    vmid     bigint       null,
    host     varchar(255) null comment '控制器连接地址',
    port     int          null comment 'vnc端口',
    username varchar(255) null,
    password varchar(255) null comment 'vnc密码'
);

create table vncnode
(
    id          int auto_increment
        primary key,
    name        varchar(255)     not null comment '别称',
    host        varchar(255)     null,
    port        int default 7600 not null comment '控制器端口',
    domain      varchar(255)     null,
    protocol    int default 0    not null comment '0=false;1=true',
    proxy       int default 0    not null comment '0=false;1=true',
    status      int default 0    not null comment '0=true；1=false',
    create_date bigint           null comment '创建日期'
);

create table vnets
(
    id    int auto_increment
        primary key,
    vnet  varchar(255)                null,
    zone  varchar(255)                null,
    alias varchar(255)                null,
    tag   int                         null,
    type  varchar(255) default 'vnet' not null,
    state varchar(255) default 'new'  not null
);

create table zones
(
    id         int auto_increment
        primary key,
    node_id    int                        null,
    type       varchar(255)               null,
    zone       varchar(255)               null,
    nodes      varchar(255)               null,
    ipam       varchar(255)               null,
    dns        varchar(255)               null,
    reversedns varchar(255)               null,
    state      varchar(255) default 'new' not null
);


