package com.chuqiyun.proxmoxveams.utils;

import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.yaml.snakeyaml.Yaml;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * @author mryunqi
 * @date 2023/11/26
 */
public class SqlUpdateLoader {
    private final ResourceLoader resourceLoader;

    public SqlUpdateLoader(ResourceLoader resourceLoader) {
        this.resourceLoader = resourceLoader;
    }

    /**
    * @Author: mryunqi
    * @Description: 获取指定版本和迭代号的 SQL 更新列表
    * @DateTime: 2023/11/26 23:53
    * @Params: String distributionVersion 发行版本
     *         String buildVersion 构建版本
     *         int iteration 迭代号
     *          例如：getSqlUpdates("build","1.0.7", 3) 返回的是 build-1.0.7-3.yml 中的 SQL 更新列表
     *          如果找不到对应的版本和迭代号，返回空列表
     *
    * @Return
    */
    public List<String> getSqlUpdates(String distributionVersion,String buildVersion, int iteration) throws IOException {
        String resourceName = "classpath:sql-update.yml";
        Resource resource = resourceLoader.getResource(resourceName);

        try (InputStream inputStream = resource.getInputStream()) {
            Yaml yaml = new Yaml();
            Map<String, Map<String, Map<Integer, List<String>>>> yamlData = yaml.load(inputStream);

            if (yamlData != null) {
                Map<String, Map<Integer, List<String>>> buildMap = yamlData.get(distributionVersion);
                if (buildMap != null) {
                    Map<Integer, List<String>> versionMap = buildMap.get(buildVersion);
                    if (versionMap != null) {
                        return versionMap.get(iteration);
                    }
                }
            }
        }
        return Collections.emptyList(); // 如果找不到对应的版本和迭代号，返回空列表
    }

    /**
     * 从JAR包内的resources目录下的script.sql文件中读取SQL语句
     *
     * @return SQL语句字符串
     */
    public static String readSqlScript() {
        StringBuilder sqlScript = new StringBuilder();

        // 使用ClassLoader加载资源
        ClassLoader classLoader = SqlUpdateLoader.class.getClassLoader();
        try (InputStream inputStream = classLoader.getResourceAsStream("script.sql")) {
            if (inputStream != null) {
                // 使用BufferedReader逐行读取SQL语句
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        sqlScript.append(line).append("\n");
                    }
                }
            } else {
                // 未找到返回空字符串
                return "";
            }
        } catch (IOException e) {
            e.printStackTrace();
            return "";
        }

        return sqlScript.toString();
    }

    /**
    * @Author: mryunqi
    * @Description: 将scripts.sql中的SQL语句分割成单条语句,并返回语句列表
    * @DateTime: 2023/12/3 16:52
    * @Return List<String> SQL语句列表
    */
    public static List<String> splitSqlScript() {
        String sqlScript = readSqlScript();
        // 使用";"分割SQL语句
        return List.of(sqlScript.split(";"));
    }

}
