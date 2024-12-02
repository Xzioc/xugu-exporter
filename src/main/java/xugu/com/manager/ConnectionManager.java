package xugu.com.manager;

import xugu.com.config.DbConfig;

import java.sql.Connection;
import java.sql.Driver;
import java.sql.DriverManager;
import java.sql.SQLException;

/**
 * @Author 熊呈
 * @Mail xiongcheng@xugudb.com
 * @Date 2024/11/28 10:55
 */
public class ConnectionManager {
    private Connection connection;
    private final DbConfig config;

    public ConnectionManager(final DbConfig dbConfig) throws Exception {
        Driver driver = (Driver) Class.forName(dbConfig.getDriver()).getDeclaredConstructor().newInstance();
        DriverManager.registerDriver(driver);
        config = dbConfig;
        connection = getConnection();
    }

    public Connection getConnection() throws SQLException {
        if (null != connection && !connection.isClosed()) {
            return connection;
        }
        String jdbcUrl =
                "jdbc:xugu://" + config.getUrl() + ":" + config.getPort() + "/" + config.getDbName();
        return connection = DriverManager.getConnection(jdbcUrl, config.getUsername(), config.getPassword());
    }
}
