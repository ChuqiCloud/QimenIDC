package com.chuqiyun.proxmoxveams.utils;

import org.apache.commons.lang3.StringUtils;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.yaml.snakeyaml.Yaml;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * @author mryunqi
 * @date 2023/11/26
 */
public class SqlUpdateLoader {
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
        Resource resource = new ClassPathResource("sql-update.yml");

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
     * @Author: 星禾
     * @Description: 获取按版本和迭代排序后的所有build升级步骤
     * @DateTime: 2026/6/7 10:49
     * @Return List<BuildSqlStep> build升级步骤
     */
    public List<BuildSqlStep> getOrderedBuildSqlSteps() throws IOException {
        Resource resource = new ClassPathResource("sql-update.yml");

        try (InputStream inputStream = resource.getInputStream()) {
            Yaml yaml = new Yaml();
            Map<String, Map<String, Map<Integer, List<String>>>> yamlData = yaml.load(inputStream);
            if (yamlData == null) {
                return Collections.emptyList();
            }
            Map<String, Map<Integer, List<String>>> buildMap = yamlData.get("build");
            if (buildMap == null || buildMap.isEmpty()) {
                return Collections.emptyList();
            }

            List<BuildSqlStep> stepList = new ArrayList<>();
            for (Map.Entry<String, Map<Integer, List<String>>> versionEntry : buildMap.entrySet()) {
                String buildVersion = versionEntry.getKey();
                Map<Integer, List<String>> versionMap = versionEntry.getValue();
                if (versionMap == null || versionMap.isEmpty()) {
                    continue;
                }
                for (Map.Entry<Integer, List<String>> iterationEntry : versionMap.entrySet()) {
                    List<String> sqlList = iterationEntry.getValue();
                    if (sqlList == null || sqlList.isEmpty()) {
                        continue;
                    }
                    stepList.add(new BuildSqlStep(buildVersion, iterationEntry.getKey(), sqlList));
                }
            }

            stepList.sort((left, right) -> compareBuildVersion(buildStepKey(left), buildStepKey(right)));
            return stepList;
        }
    }

    /**
     * @Author: 星禾
     * @Description: 比较build版号先后顺序
     * @DateTime: 2026/6/7 10:49
     * @Params: String left 左侧build版号
     * @Params: String right 右侧build版号
     * @Return int 左侧小于右侧返回负数，相等返回0，大于返回正数
     */
    public static int compareBuildVersion(String left, String right) {
        BuildVersion leftVersion = parseBuildVersion(left);
        BuildVersion rightVersion = parseBuildVersion(right);

        int versionCompare = compareVersionParts(leftVersion.versionParts, rightVersion.versionParts);
        if (versionCompare != 0) {
            return versionCompare;
        }
        return Integer.compare(leftVersion.iteration, rightVersion.iteration);
    }

    private static String buildStepKey(BuildSqlStep buildSqlStep) {
        return buildSqlStep.getBuildVersion() + "_" + buildSqlStep.getIteration();
    }

    private static int compareVersionParts(int[] leftParts, int[] rightParts) {
        int length = Math.max(leftParts.length, rightParts.length);
        for (int i = 0; i < length; i++) {
            int leftValue = i < leftParts.length ? leftParts[i] : 0;
            int rightValue = i < rightParts.length ? rightParts[i] : 0;
            int result = Integer.compare(leftValue, rightValue);
            if (result != 0) {
                return result;
            }
        }
        return 0;
    }

    private static BuildVersion parseBuildVersion(String buildVersion) {
        if (StringUtils.isBlank(buildVersion)) {
            return new BuildVersion(new int[0], 0);
        }
        String trimmedBuildVersion = StringUtils.trim(buildVersion);
        String[] buildVersionParts = StringUtils.split(trimmedBuildVersion, "_", 2);
        String versionPart = buildVersionParts.length > 0 ? buildVersionParts[0] : "";
        int iteration = 0;
        if (buildVersionParts.length > 1) {
            try {
                iteration = Integer.parseInt(buildVersionParts[1]);
            } catch (NumberFormatException ignored) {
                iteration = 0;
            }
        }
        String[] versionParts = StringUtils.split(versionPart, ".");
        int[] parsedVersionParts = new int[versionParts == null ? 0 : versionParts.length];
        if (versionParts != null) {
            for (int i = 0; i < versionParts.length; i++) {
                try {
                    parsedVersionParts[i] = Integer.parseInt(versionParts[i]);
                } catch (NumberFormatException ignored) {
                    parsedVersionParts[i] = 0;
                }
            }
        }
        return new BuildVersion(parsedVersionParts, iteration);
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

    /**
     * Build版本信息
     */
    public static final class BuildSqlStep {
        private final String buildVersion;
        private final int iteration;
        private final List<String> sqlList;

        public BuildSqlStep(String buildVersion, int iteration, List<String> sqlList) {
            this.buildVersion = buildVersion;
            this.iteration = iteration;
            this.sqlList = Collections.unmodifiableList(new ArrayList<>(sqlList));
        }

        public String getBuildVersion() {
            return buildVersion;
        }

        public int getIteration() {
            return iteration;
        }

        public List<String> getSqlList() {
            return sqlList;
        }
    }

    private static final class BuildVersion {
        private final int[] versionParts;
        private final int iteration;

        private BuildVersion(int[] versionParts, int iteration) {
            this.versionParts = versionParts;
            this.iteration = iteration;
        }
    }

}
