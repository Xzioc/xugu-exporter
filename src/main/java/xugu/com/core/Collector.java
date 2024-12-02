package xugu.com.core;

import io.prometheus.client.CollectorRegistry;
import io.prometheus.client.Gauge;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import xugu.com.config.DbConfig;
import xugu.com.config.QueryConfig;
import xugu.com.manager.ConnectionManager;

import java.io.IOException;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

/**
 * @Author 熊呈
 * @Mail xiongcheng@xugudb.com
 * @Date 2024/11/28 11:42
 */
@Slf4j
public class Collector {
    private final Object requestLock = new ReentrantLock();
    private final Thread sqlThread;
    private final CollectorRegistry registry;
    ConnectionManager connectionManager;
    QueryConfig queryConfig;
    DbConfig dbConfig;
    private final Map<String, Gauge> gauges = new HashMap<>();
    private PreparedStatement preparedStatement = null;
    //记录上次成功毫秒数
    private volatile long lastSuccessTs = 0;
    //收集计数
    private volatile long collectCount = 0;

    public Collector(ConnectionManager connectionManager, QueryConfig queryConfig,
                     CollectorRegistry registry, DbConfig dbConfig) {
        this.connectionManager = connectionManager;
        this.queryConfig = queryConfig;
        this.registry = registry;
        this.dbConfig = dbConfig;
        sqlThread = new Thread(() -> {
            ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
            scheduler.scheduleAtFixedRate(() -> {
                try {
                    collectData();
                } catch (SQLException e) {
                    log.error("执行sql收集失败" + e.getLocalizedMessage());
                }
            }, 0, queryConfig.getInterval(), TimeUnit.SECONDS);
        });
        if (!queryConfig.isMultiRow()) {
            try {
                PreparedStatement statement = getStatement();
                statement.execute();
                ResultSetMetaData metadata = statement.getMetaData();
                int columnCount = metadata.getColumnCount();
                for (int i = 1; i <= columnCount; i++) {
                    String columnName = metadata.getColumnName(i);
                    String columnTypeStr = metadata.getColumnTypeName(i);
                    int columnType = metadata.getColumnType(i);
                    String columnLabel = metadata.getColumnLabel(i);
                    if (!isColumnTypeNumeric(columnType)) {
                        continue;
                    }
                    String gaugeName = getGaugeName(columnName, null);
                    log.info("注册收集器：列 {} 类型为 {} （指标名称为 '{}'）", columnName, columnTypeStr, gaugeName);
                    getGauge(gaugeName, columnLabel);
                    if (gauges.isEmpty()) {
                        log.info("没有收集到任何数据，SQL为：{}", queryConfig.getSql());
                        return;
                    }
                }
            } catch (SQLException | IOException e) {
                log.error("执行sql收集失败", e);
                throw new RuntimeException(e);
            }

        }
    }

    private void collectData() throws SQLException {
        synchronized (requestLock) {
            if (!queryConfig.isMultiRow() && gauges.isEmpty()) {
                return;
            }
            log.info("开始收集指标: {},sql为：{}", queryConfig.getRemark(), queryConfig.getSql());
            try {
                ResultSet rs = getStatement().executeQuery();
                ResultSetMetaData meta = rs.getMetaData();
                while (rs.next()) {
                    int columnCount = meta.getColumnCount();
                    String multiRowName = "";
                    for (int i = 1; i <= columnCount; i++) {
                        String columnName = meta.getColumnName(i);
                        final Gauge gauge;
                        if (queryConfig.isMultiRow()) {
                            if (1 == i) {
                                multiRowName = rs.getString(i);
                                continue;
                            }
                            if (!isColumnTypeNumeric(meta.getColumnType(i))) {
                                continue;
                            }
                            String gaugeName = getGaugeName(columnName, multiRowName);
                            gauge = getGauge(gaugeName, columnName);
                        } else {
                            if (!isColumnTypeNumeric(meta.getColumnType(i))) {
                                continue;
                            }
                            gauge = gauges.get(getGaugeName(columnName, null));
                            if (null == gauge) {
                                continue;
                            }
                        }
                        double value = rs.getDouble(i);
                        gauge.labels(dbConfig.getUrl() + ":" + dbConfig.getPort())
                                .set(value);
                    }
                    if (!queryConfig.isMultiRow()) {
                        break;
                    }
                }
                rs.close();
                collectCount++;
                lastSuccessTs = System.currentTimeMillis();
                if (1 == collectCount) {
                    sqlThread.start();
                }
            } catch (Exception e) {
                log.error("执行sql收集失败", e);
                for (Map.Entry<String, Gauge> gaugeEntry : gauges.entrySet()) {
                    Gauge gauge = gaugeEntry.getValue();
                    registry.unregister(gauge);
                }
                gauges.clear();
            }
        }
    }

    private PreparedStatement getStatement() throws SQLException, IOException {
        if (null == preparedStatement || preparedStatement.isClosed()) {
            preparedStatement = connectionManager.getConnection().prepareStatement(queryConfig.getSql());
        }
        return preparedStatement;
    }

    private boolean isColumnTypeNumeric(int columnType) {

        switch (columnType) {
            case java.sql.Types.BIGINT:
            case java.sql.Types.INTEGER:
            case java.sql.Types.DOUBLE:
            case java.sql.Types.FLOAT:
            case java.sql.Types.NUMERIC:
            case java.sql.Types.REAL:
            case java.sql.Types.VARCHAR:
            case java.sql.Types.SMALLINT:
            case java.sql.Types.TINYINT:
            case java.sql.Types.DECIMAL:
                return true;
        }
        return false;
    }

    private String getGaugeName(String columnName, String rowName) {
        String result = "";

        if (StringUtils.isNotBlank(queryConfig.getPrefix())) {
            result += queryConfig.getPrefix() + "__";
        }
        if (StringUtils.isNotBlank(rowName)) {
            result += rowName + "__";
        }
        result += columnName;
        return result.replaceAll("[^_A-Za-z0-9]", "");
    }

    private Gauge getGauge(String gaugeName, final String help) {
        Gauge gauge = gauges.get(gaugeName);
        if (Objects.nonNull(gauge)) {
            return gauge;
        }
        log.info("注册指标: " + gaugeName);
        Gauge result = Gauge.build()
                .name(gaugeName)
                .help(help)
                .labelNames("hostname")
                .register();
        gauges.put(gaugeName, result);
        return result;
    }

    public void collectNow(int millisTolerance) {
        if (System.currentTimeMillis() - lastSuccessTs > millisTolerance) {
            try {
                collectData();
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        }
    }
}
