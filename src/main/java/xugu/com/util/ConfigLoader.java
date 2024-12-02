package xugu.com.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import xugu.com.config.DbConfig;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Properties;

/**
 * @Author 熊呈
 * @Mail xiongcheng@xugudb.com
 * @Date 2024/11/28 10:22
 */
public class ConfigLoader {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    //读取查询指标配置信息
    public static <T> T loadJsonConfig(String filePath, Class<T> clazz) {
        try {
            return OBJECT_MAPPER.readValue(new File(filePath), clazz);
        } catch (IOException e) {
            throw new RuntimeException("加载文件失败： " + filePath, e);
        }
    }

    //读取db配置信息
    public static DbConfig loadPropertiesConfig(String filePath) {
        Properties properties = new Properties();
        try (FileInputStream input = new FileInputStream(filePath)) {
            properties.load(input);
        } catch (IOException e) {
            throw new RuntimeException("加载文件失败： " + filePath, e);
        }

        DbConfig config = new DbConfig();
        config.setUrl(properties.getProperty("jdbc.url"));
        config.setUsername(properties.getProperty("jdbc.username"));
        config.setPassword(properties.getProperty("jdbc.password"));
        config.setPort(properties.getProperty("jdbc.port"));
        config.setDriver(properties.getProperty("jdbc.driver"));
        config.setDbName(properties.getProperty("jdbc.dbname"));
        config.setPrometheusPort(properties.getProperty("prometheus.port"));
        return config;
    }
}
