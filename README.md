[![pPYJ8PS.png](/screenshot/logo.png)](https://github.com/ChuqiCloud/QimenIDC)

# QimenIDC - 云原生的多云管理及混合云融合系统 #

![Static Badge](https://img.shields.io/badge/SpringBoot-2.7.5-green?style=flat-square&logo=springboot&logoColor=%236DB33F)
![Static Badge](https://img.shields.io/badge/OpenJDK-17%2B-green?style=flat-square&logo=openjdk&logoColor=%23FFFFFF)
![Static Badge](https://img.shields.io/badge/Proxmox-7.0%2B-green?style=flat-square&logo=proxmox&logoColor=%23E57000)
---

[简体中文](./README.md) | [English](./README.en.md)

本项目是一个开源、免费、云原生的多云管理及混合云融合系统，它致力于简化多云环境下的资源管理和操作，提高管理多云的效率。

## 主要特性

- **多云管理**：通过隐藏不同云服务商的数据模型和 API 差异，QimenIDC 提供了一套统一的 API，让用户能够像使用单一云平台一样访问多个云服务商。
- **云原生架构**：QimenIDC 是一个云原生系统，充分利用容器化和微服务架构的优势，提供弹性扩展、高可用性和灵活部署等特性。
- **混合云融合**：QimenIDC 通过整合不同的云服务提供商，包括目前已融合的 ProxmoxVE，实现了混合云环境下资源的统一管理和协同操作。
- **简化复杂性**：QimenIDC 通过抽象底层基础设施的细节和差异，提供了简洁而一致的接口，使用户能够轻松地管理和操作多云环境，无需关注底层复杂性。
- **高效管理**：QimenIDC 提供了功能强大的管理工具和自动化机制，帮助用户快速配置、监控和调整多云环境中的资源，从而提高管理效率。

本项目为社区免费开源项目，欢迎大家使用和贡献代码。

企业级用户可等待后续发布的商业版，商业版将提供更多功能和服务。

企业版架构将与社区版完全不同，企业版将采用更规范、更稳定、更安全的架构，以满足企业级用户的需求。

## 登录安全

支持 TOTP 动态口令与 Passkey（WebAuthn）二步验证，以及管理员自助重置密码。
详见 [二步验证与 Passkey 登录](/docs/mfa-passkey.md)。

## 开发者API文档

离线文档：
[开发者API文档](/docs/API.md)

最新在线文档：
[https://apifox.com/apidoc/shared-56015960-c9d9-488b-b53d-d9b336ec60bd](https://apifox.com/apidoc/shared-56015960-c9d9-488b-b53d-d9b336ec60bd)

## 前端项目
[前端部署](https://docs.acmecloud.cn/docs/qimenidc/installation/client)

## 快速开始

### 项目结构概述

该项目分主控与节点两部分，主控为管理节点，节点为被管理节点。

被控部署在宿主机上，主控可部署在任意服务器上或者在容器中。

主控最低运行配置要求：

- CPU：2核
- 内存：4G
- 硬盘：20G

### 程序获取

**前置环境：**

- [Java SE Development Kits - 17](https://www.oracle.com/java/technologies/javase/jdk17-archive-downloads.html)或更高版本
- [MySQL - 5.7](https://dev.mysql.com/downloads/mysql/5.7.html)或更高版本

> 注意：请务必使用MySQL 5.7+与JDK17+版本，否则会导致程序无法正常运行。

你可以直接下载已经构建好的 QimenIDC 发行版

或者，你也可以从源代码构建 QimenIDC，下方有构建指南。

#### 自行构建

QimenIDC 使用 Gradle 进行构建，并且需要在 JDK 17 环境下运行。

**前置：**
- [Git](https://git-scm.com/downloads)

**步骤：**

#### Windows

   ```shell
   git clone https://cnb.cool/acmecloud/QimenIDC.git
   cd QimenIDC
   .\gradlew.bat # 设置开发环境
   .\gradlew build # 编译
   ```

#### Linux (GNU)

   ```bash
    git clone https://cnb.cool/acmecloud/QimenIDC.git
    cd QimenIDC
    chmod +x gradlew # 设置可执行权限
    ./gradlew build # 编译
   ```

你可以在项目根目录下的/build/lib找到输出的jar。

### 主控部署

请将config目录与jar包放在同一目录下。

配置文件说明：

主配置文件：application.yml

```yaml
config:
   profiles: prod # 配置文件环境，可选值：dev、prod、test
```
profiles的内容与config目录下的文件名对应，例如：profiles为prod时，会加载application-prod.yml文件。

可根据自己的需要修改配置文件。

使用命令行运行：

```shell
java -jar QimenIDC.jar
```

可根据自己服务器的配置修改启动参数。

### 节点部署

被控程序在Controlled目录下，请将Controlled目录下的install.sh文件上传到被控服务器上，然后执行该脚本即可。

或者可以运行以下命令：

```shell
wget -O install.sh http://mirror.chuqiyun.com/software/QAgent/install.sh && bash install.sh
```

注意：如何你不是pve的企业订阅用户，需要先删除pve官方的企业源，否则会导致安装失败。

更换源教程：

[https://docs.acmecloud.cn/docs/qimenidc/replace-mirror-source](https://docs.acmecloud.cn/docs/qimenidc/replace-mirror-source)

### 节点端口开放（宿主机）
    
    7600/tcp
    8006/tcp
    6080/tcp
    59000-65535/tcp

> 注意：主控不再依赖节点SSH，任务统一由被控（QAgent）下发，请确保 7600/tcp 可访问（该端口由 nginx 提供 TLS）。

### 主控与被控通信安全

被控端只监听回环地址（`127.0.0.1:17600`），由 nginx 在 7600/tcp 上终止 TLS 并按管理端源 IP/CIDR 白名单放行。除此之外：

- **加密负载**：写操作请求体与全部响应体使用 AES-256-GCM 加密，密钥为被控端 token 的 SHA-256 摘要，Web 面板抓不到明文（含密码、镜像路径等）。
- **防重放**：每个请求带 `X-QimenIDC-Timestamp`、`X-QimenIDC-Nonce` 头，两者参与 AEAD 校验；被控端拒绝超时与重复的请求。
- **可写范围白名单**：被控端文件类接口只允许操作白名单目录（默认只有 `/var/lib/vz/snippets`、`/etc/network`、`/home/images`），越权请求返回 403。
- **已下线接口**：NAT、迁移、更新以及文件读写删除下载类接口一律返回 410，主控侧对应流程同步停用。
- **白名单自动同步**：主控启动时把集群内所有主控出口地址经 `POST /net/trust` 下发到各节点，被控端再并入自己实测到的对端地址后重载 nginx；出口 IP 变了不用再手工改 `security.conf`。该接口保留鉴权、只跳过 IP 白名单，是主控换 IP 后唯一的自救通道。管理端 `GET /{adminPath}/getAgentWhitelist` 可查同步状态。

> 部署与证书导入步骤见 [docs/controlled-node-security.md](docs/controlled-node-security.md)。升级时先更新被控端（QAgent），再更新主控。

## 社区和支持
如果您有任何问题、建议或反馈，可以加入QQ群：

![qq 社群](/screenshot/qq.png)

## 致谢与社区赞助

感谢以下云厂商为本项目提供的云产品资源支持，助力 QimenIDC 开源社区的持续发展：

| 初七云 | 无忧云 |
|:---:|:---:|
| ![初七云](/screenshot/logo2.png) | ![无忧云](/screenshot/wuyou.png) |
| [初七云](https://www.chuqiyun.com/) | [无忧云](https://www.wuyouyun.com/) |

正是有这些开源社区赞助商的支持，QimenIDC 才能不断完善与迭代。我们欢迎更多的企业、厂商和个人参与到开源社区建设中来，共同推动多云管理生态的发展。

> 如果您所在的企业或组织希望成为 QimenIDC 的赞助方，欢迎通过下方 QQ 群联系我们。

## 许可证

QimenIDC 使用 [AGPL-3.0 License](https://www.gnu.org/licenses/agpl-3.0.html) 进行许可。

---

感谢您对 QimenIDC 项目的关注和支持！我们期待您的贡献，希望 QimenIDC 能为您提供便捷的多云管理和混合云融合体验。如果您对该项目有任何疑问或需要帮助，请随时与我们联系。

## 项目截图

| | |
|:---:|:---:|
| ![00.png](/screenshot/00.png) | ![01.png](/screenshot/01.png) |
| ![02.png](/screenshot/02.png) | ![03.png](/screenshot/03.png) |
| ![04.png](/screenshot/04.png) | ![05.png](/screenshot/05.png) |
| ![06.png](/screenshot/06.png) | ![07.png](/screenshot/07.png) |
| ![08.png](/screenshot/08.png) | ![09.png](/screenshot/09.png) |
| ![10.png](/screenshot/10.png) | ![11.png](/screenshot/11.png) |
| ![12.png](/screenshot/12.png) | ![13.png](/screenshot/13.png) |
| ![14.png](/screenshot/14.png) | ![15.png](/screenshot/15.png) |
| ![16.png](/screenshot/16.png) | |