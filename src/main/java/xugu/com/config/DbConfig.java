package xugu.com.config;

import lombok.Data;

/**
 * @Author 熊呈
 * @Mail xiongcheng@xugudb.com
 * @Date 2024/11/28 9:48
 */
@Data
public class DbConfig {
    String url;
    String username;
    String password;
    String dbName;
    String port;
    String driver;
    String prometheusPort;
}
