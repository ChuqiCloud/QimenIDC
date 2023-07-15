# QimenIDC - Cloud-Native Multi-Cloud Management and Hybrid Cloud Integration System


Welcome to the GitHub page of QimenIDC! This project is an open-source, free, and cloud-native system designed for managing and operating resources in multi-cloud environments, with the aim of improving the efficiency of multi-cloud management. QimenIDS is built using Gradle and requires JDK 17 for execution. The project is primarily hosted on Gitee, and its address is: [https://gitee.com/chuqicloud/QimenIDS](https://gitee.com/chuqicloud/QimenIDS).

## Key Features

- **Multi-Cloud Management**: QimenIDS provides a unified API that hides the differences in data models and APIs among various cloud service providers, allowing users to access multiple cloud providers as if they were using a single cloud platform.
- **Cloud-Native Architecture**: QimenIDS is a cloud-native system that leverages containerization and microservices architecture, offering advantages such as elastic scalability, high availability, and flexible deployment.
- **Hybrid Cloud Integration**: By integrating different cloud service providers, including the currently integrated ProxmoxVE, QimenIDS enables unified management and collaborative operations for resources in hybrid cloud environments.
- **Simplified Complexity**: QimenIDS abstracts the details and differences of underlying infrastructure, providing a simple and consistent interface that enables users to easily manage and operate in multi-cloud environments without the need to focus on underlying complexities.
- **Efficient Management**: QimenIDS offers powerful management tools and automation mechanisms to assist users in quickly configuring, monitoring, and adjusting resources in multi-cloud environments, thereby improving management efficiency.

## Quick Start

Here are the basic steps to get started with QimenIDS in your local environment:

1. Clone the project code to your local machine:

   ```shell
   git clone https://gitee.com/chuqicloud/QimenIDS.git
   ```

2. Navigate to the project directory:

   ```shell
   cd QimenIDS
   ```

3. Configure the authentication information for the database and cloud service providers in the `/config/application.yml` file.

4. Ensure that you have JDK 17 installed and configured as the runtime environment for the project.

5. Build the QimenIDS project using Gradle:

   ```shell
   ./gradlew build
   ```

6. Start QimenIDS:

   ```shell
   ./gradlew bootRun
   ```

Detailed deployment and configuration guides can be found in the [documentation](https://www.chuqiyun.com).

## Community and Support

If you have any questions, suggestions, or feedback, feel free to reach out to us via email:

- Email: cloud@chuqis.com

You can report issues or submit feature requests on our [GitHub Issues](https://github.com/your-username/QimenIDS/issues) page.

## License

QimenIDS is licensed under the [AGPL-3.0 License](https://www.gnu.org/licenses/agpl-3.0.html).

---

Thank you for your interest and support in QimenIDS! We look forward to your contributions and hope that QimenIDS will provide you with a convenient experience for multi-cloud management and hybrid cloud integration. If you have any questions or need assistance regarding this project, please don't hesitate to contact us.