# QimenIDC - Cloud-Native Multi-Cloud Management and Hybrid Cloud Integration System

[简体中文](./README.md) | [English](./README.en.md)

This project is an open-source, free, cloud-native multi-cloud management and hybrid cloud integration system. It aims to simplify resource management and operations in multi-cloud environments, enhancing the efficiency of managing multiple clouds.

The project is primarily hosted on Gitee at: [https://gitee.com/chuqicloud/QimenIDC](https://gitee.com/chuqicloud/QimenIDC).

## Key Features

- **Multi-Cloud Management**: QimenIDC provides a unified API that hides the data models and API differences of various cloud service providers. This allows users to access multiple cloud service providers as if they were using a single cloud platform.

- **Cloud-Native Architecture**: QimenIDC is a cloud-native system that leverages containerization and microservices architecture. It offers features such as elastic scalability, high availability, and flexible deployment.

- **Hybrid Cloud Integration**: QimenIDC integrates different cloud service providers, including the currently integrated ProxmoxVE, to achieve unified management and collaborative operations in a hybrid cloud environment.

- **Simplified Complexity**: By abstracting the details and differences of underlying infrastructure, QimenIDC provides a concise and consistent interface, enabling users to easily manage and operate in multi-cloud environments without having to worry about underlying complexity.

- **Efficient Management**: QimenIDC offers powerful management tools and automation mechanisms to help users quickly configure, monitor, and adjust resources in multi-cloud environments, thus improving management efficiency.

This project is a community-driven open-source project, and everyone is welcome to use it and contribute code.

Enterprise-level users can look forward to the upcoming commercial version, which will provide additional features and services, including but not limited to:

- Extended Support
- Advanced Management Feature Support
- Advanced Monitoring Feature Support
- Advanced Automation Feature Support
- Advanced Security Feature Support
- Advanced Networking Feature Support
- Advanced Storage Feature Support
- Advanced Compute Feature Support
- Advanced Container Feature Support
- Advanced Application Feature Support
- Disaster Recovery Backup Feature Support

The architecture of the enterprise edition will be entirely different from the community edition, designed to meet the needs of enterprise-level users with more standardized, stable, and secure architecture.

## Developer API Documentation

[https://apifox.com/apidoc/shared-56015960-c9d9-488b-b53d-d9b336ec60bd](https://apifox.com/apidoc/shared-56015960-c9d9-488b-b53d-d9b336ec60bd)

## Quick Start

### Project Structure Overview

This project consists of two parts: the controller and the node. The controller is the management node, while the node is the managed node.

The node is deployed on the host machine, while the controller can be deployed on any server or in a container.

Minimum system requirements for the controller:

- CPU: 2 cores
- Memory: 4GB
- Hard Disk: 20GB

### Node Deployment

The controlled program is located in the Controlled directory. Upload the install.sh file from the Controlled directory to the controlled server and execute the script.

Alternatively, you can run the following command:

```shell
wget -O install.sh http://mirrors.leapteam.cn:8899/software/controlled/install.sh && bash install.sh
```

### Node Port Openings

Port open on the controlled server:

- 7600/tcp

Port open on the host machine:

- 22/tcp
- 8006/tcp

SSH service needs to be enabled on the host machine.

### Controller Deployment

#### Program Retrieval

You can either download the pre-built QimenIDC distribution or build it from source code. There are build instructions below.

Place the config directory and jar file in the same directory.

Explanation of configuration files:

Main configuration file: application.yml

```yaml
config:
   profiles: prod # Configuration file environment, optional values: dev, prod, test
```

The contents of profiles correspond to the file names in the config directory. For example, when profiles is prod, it will load the application-prod.yml file.

Modify the configuration file according to your needs.

Run from the command line:

```shell
java -jar QimenIDC.jar
```

Modify the startup parameters according to your server's configuration.

### Self-Build

QimenIDC uses Gradle for building and requires JDK 17 environment to run.

**Prerequisites:**

- [Java SE Development Kits - 17](https://www.oracle.com/java/technologies/javase/jdk17-archive-downloads.html) or higher versions
- [Git](https://git-scm.com/downloads)

**Steps:**

#### Windows

```shell
git clone https://gitee.com/chuqicloud/QimenIDC.git
cd QimenIDC
.\gradlew.bat # Set up the development environment
.\gradlew jar # Compile
```

#### Linux (GNU)

```bash
git clone https://gitee.com/chuqicloud/QimenIDC.git
cd QimenIDC
chmod +x gradlew # Set executable permissions
./gradlew jar # Compile
```

You can find the output jar in the project's root directory.

## Community and Support

If you have any questions, suggestions, or feedback, you can send them to our email:

- Mailing List: cloud@chuqis.com

You can also report issues or make feature requests on our [GitHub Issues](https://github.com/your-username/QimenIDC/issues) page.

## License

QimenIDC is licensed under the [AGPL-3.0 License](https://www.gnu.org/licenses/agpl-3.0.html).

---

Thank you for your interest and support for the QimenIDC project! We look forward to your contributions and hope that QimenIDC provides you with a convenient multi-cloud management and hybrid cloud integration experience. If you have any questions or need assistance with this project, please feel free to contact us.