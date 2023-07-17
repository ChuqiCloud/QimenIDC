# QimenIDC - 云原生的多云管理及混合云融合系统


[简体中文](./README.md) | [English](./README.en.md)

本项目是一个开源、免费、云原生的多云管理及混合云融合系统，它致力于简化多云环境下的资源管理和操作，提高管理多云的效率。

QimenIDC 使用 Gradle 进行构建，并且需要在 JDK 17 环境下运行。

项目主要托管于码云，地址为：[https://gitee.com/chuqicloud/QimenIDS](https://gitee.com/chuqicloud/QimenIDS)。

## 主要特性

- **多云管理**：通过隐藏不同云服务商的数据模型和 API 差异，QimenIDC 提供了一套统一的 API，让用户能够像使用单一云平台一样访问多个云服务商。
- **云原生架构**：QimenIDC 是一个云原生系统，充分利用容器化和微服务架构的优势，提供弹性扩展、高可用性和灵活部署等特性。
- **混合云融合**：QimenIDC 通过整合不同的云服务提供商，包括目前已融合的 ProxmoxVE，实现了混合云环境下资源的统一管理和协同操作。
- **简化复杂性**：QimenIDC 通过抽象底层基础设施的细节和差异，提供了简洁而一致的接口，使用户能够轻松地管理和操作多云环境，无需关注底层复杂性。
- **高效管理**：QimenIDC 提供了功能强大的管理工具和自动化机制，帮助用户快速配置、监控和调整多云环境中的资源，从而提高管理效率。

## 快速开始

以下是在本地环境中启动 QimenIDC 的基本步骤：

1. 克隆项目代码到本地：

   ```shell
   git clone https://gitee.com/chuqicloud/QimenIDS.git
   ```

2. 进入项目目录：

   ```shell
   cd QimenIDS
   ```

3. 配置数据库和云服务提供商的认证信息，可以通过 `/config/application.yml` 文件进行配置。

4. 确保您已经安装 JDK 17，并将其配置为项目的运行环境。

5. 使用 Gradle 进行构建：

   ```shell
   ./gradlew build
   ```

6. 启动 QimenIDC：

   ```shell
   ./gradlew bootRun
   ```

详细的部署和配置指南可以在 [文档链接](https://www.chuqiyun.com) 中找到。


## 社区和支持

如果您有任何问题、建议或反馈，可以发送至我们的邮箱：

- 邮件列表：cloud@chuqis.com

您可以在我们的 [GitHub Issues](https://github.com/your-username/QimenIDS/issues) 页面报告问题或提出功能请求。

## 许可证

QimenIDC 使用 [AGPL-3.0 License](https://www.gnu.org/licenses/agpl-3.0.html) 进行许可。

---

感谢您对 QimenIDC 项目的关注和支持！我们期待您的贡献，希望 QimenIDC 能为您提供便捷的多云管理和混合云融合体验。如果您对该项目有任何疑问或需要帮助，请随时与我们联系。
