package com.aeye.app.deploy.util;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class ScriptShUpdater {

    // 数据库连接配置 - 请根据实际情况修改
    private static final String DB_URL = "jdbc:mysql://192.168.16.147:3306/medicare_test?useUnicode=true&characterEncoding=UTF-8&useSSL=false&serverTimezone=Asia/Shanghai";
    private static final String DB_USER = "root";
    private static final String DB_PASSWORD = "123456";

    // 脚本目录
    private static final String SCRIPT_DIR = "C:\\Users\\hend\\Desktop\\fsdownload\\scripts";

    // app_code 与脚本文件的映射关系
    private static final Map<String, String> APP_SCRIPT_MAP = new HashMap<>();

    static {
        APP_SCRIPT_MAP.put("medical-platform", "medical-platform.sh");
        APP_SCRIPT_MAP.put("single-portal-scene", "single-portal-scene.sh");
//        APP_SCRIPT_MAP.put("medical-platform", "medical-platform.cmd");
//        APP_SCRIPT_MAP.put("single-portal-scene", "single-portal-scene.cmd");
    }

    public static void main(String[] args) {
        System.out.println("========================================");
        System.out.println("开始更新 t_ver_info 表的 script_sh 字段");
        System.out.println("========================================");

        int successCount = 0;
        int failCount = 0;
        int skipCount = 0;

        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD)) {
            conn.setAutoCommit(false);

            String updateSql = "UPDATE t_ver_info SET script_sh = ? WHERE app_code = ?";

            try (PreparedStatement ps = conn.prepareStatement(updateSql)) {
                for (Map.Entry<String, String> entry : APP_SCRIPT_MAP.entrySet()) {
                    String appCode = entry.getKey();
                    String scriptFile = entry.getValue();

                    if (scriptFile == null) {
                        System.out.println("[跳过] " + appCode + " - 无对应脚本文件");
                        skipCount++;
                        continue;
                    }

                    Path scriptPath = Paths.get(SCRIPT_DIR, scriptFile);

                    if (!Files.exists(scriptPath)) {
                        System.out.println("[跳过] " + appCode + " - 脚本文件不存在: " + scriptPath);
                        skipCount++;
                        continue;
                    }

                    try {
                        String scriptContent = readFileContent(scriptPath);
                        ps.setString(1, scriptContent);
                        ps.setString(2, appCode);

                        int rows = ps.executeUpdate();
                        if (rows > 0) {
                            System.out.println("[成功] " + appCode + " <- " + scriptFile + " (" + scriptContent.length() + " 字符)");
                            successCount++;
                        } else {
                            System.out.println("[警告] " + appCode + " - 未找到匹配记录");
                            failCount++;
                        }
                    } catch (IOException e) {
                        System.out.println("[失败] " + appCode + " - 读取文件失败: " + e.getMessage());
                        failCount++;
                    }
                }

                conn.commit();
                System.out.println();
                System.out.println("========================================");
                System.out.println("更新完成!");
                System.out.println("成功: " + successCount);
                System.out.println("失败: " + failCount);
                System.out.println("跳过: " + skipCount);
                System.out.println("========================================");

            } catch (SQLException e) {
                conn.rollback();
                System.err.println("数据库操作失败，已回滚: " + e.getMessage());
                e.printStackTrace();
            }

        } catch (SQLException e) {
            System.err.println("数据库连接失败: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * 读取文件内容（兼容Java 8）
     */
    private static String readFileContent(Path path) throws IOException {
        try (Stream<String> lines = Files.lines(path, StandardCharsets.UTF_8)) {
            return lines.collect(Collectors.joining("\n"));
        }
    }

}
