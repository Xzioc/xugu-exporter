package xugu.com;

import io.prometheus.client.CollectorRegistry;
import io.prometheus.client.exporter.MetricsServlet;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.jetty.security.ConstraintMapping;
import org.eclipse.jetty.security.ConstraintSecurityHandler;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.eclipse.jetty.util.security.Constraint;
import xugu.com.config.DbConfig;
import xugu.com.config.QueryConfigWrapper;
import xugu.com.core.Collector;
import xugu.com.manager.ConnectionManager;
import xugu.com.util.ConfigLoader;

import java.io.IOException;
import java.net.BindException;
import java.util.LinkedList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * @Author 熊呈
 * @Mail xiongcheng@xugudb.com
 * @Date 2024/11/28 9:39
 */
@Slf4j
public class Main {
    public static void main(String[] args) throws Exception {

        String databaseConfigFilePath = "./dbConfig.properties";
        String queryConfigFilePath = "./collect.json";

        DbConfig databaseConfig = ConfigLoader.loadPropertiesConfig(databaseConfigFilePath);
        ConnectionManager connectionManager = new ConnectionManager(databaseConfig);

        log.info("将监控 {}:{} 信息", databaseConfig.getUrl(), databaseConfig.getPort());

        QueryConfigWrapper queryConfigs = ConfigLoader.loadJsonConfig(queryConfigFilePath, QueryConfigWrapper.class);
        log.info("将收集以下指标:");
        queryConfigs.getQueries().forEach(queryConfigItem -> log.info(queryConfigItem.getName()));

        Server server = null;
        try {
            CollectorRegistry registry = CollectorRegistry.defaultRegistry;

            // 初始化并验证所有指标
            List<Collector> collectors = initializeCollectors(connectionManager, queryConfigs, registry, databaseConfig);
            log.info("所有指标验证通过");

            // 启动 Jetty 服务器
            server = startJettyServer(databaseConfig, collectors, registry);
            String asciiArt =
                    "\n __    __  _   _   _____   _   _   _____  __    __  _____   _____   _____    _____   _____   " +
                    "_____   \n" +
                    " \\ \\  / / | | | | /  ___| | | | | | ____| \\ \\  / / |  _  \\ /  _  \\ |  _  \\  |_   _| | ____| |  _  \\  \n" +
                    "  \\ \\/ /  | | | | | |     | | | | | |__    \\ \\/ /  | |_| | | | | | | |_| |    | |   | |__   | |_| |  \n" +
                    "   }  {   | | | | | |  _  | | | | |  __|    }  {   |  ___/ | | | | |  _  /    | |   |  __|  |  _  /  \n" +
                    "  / /\\ \\  | |_| | | |_| | | |_| | | |___   / /\\ \\  | |     | |_| | | | \\ \\    | |   | |___  | | \\ \\  \n" +
                    " /_/  \\_\\ \\_____/ \\_____/ \\_____/ |_____| /_/  \\_\\ |_|     \\_____/ |_|  \\_\\   |_|   " +
                            "|_____| |_|  \\_\\\n";

            System.out.println(asciiArt);
            log.info("XuguDB Prometheus Exporter 启动成功，端口: {}", databaseConfig.getPrometheusPort());
            server.join();
        } catch (Exception e) {
            handleException(e, server, databaseConfig);
        }
    }

    private static List<Collector> initializeCollectors(ConnectionManager connectionManager, QueryConfigWrapper queryConfigs,
                                                        CollectorRegistry registry, DbConfig databaseConfig) {
        List<Collector> collectors = new LinkedList<>();
        queryConfigs.getQueries().forEach(queryConfig ->
                collectors.add(new Collector(connectionManager, queryConfig, registry, databaseConfig)));

        log.info("开始验证收集指标...");
        collectors.forEach(collector -> collector.collectNow(12));
        return collectors;
    }

    /**
     * 启动 Jetty 服务器
     */
    private static Server startJettyServer(DbConfig databaseConfig, List<Collector> collectors, CollectorRegistry registry) throws Exception {
        ServletContextHandler context = new ServletContextHandler();
        context.setContextPath("/");

        // 配置 Prometheus 的 "/metrics" 和 "/metrics_now" 路径
        context.addServlet(new ServletHolder(new MetricsServlet(registry)), "/metrics");
        context.addServlet(createMetricsNowServlet(collectors), "/metrics_now");
        context.addServlet(new ServletHolder(new MetricsServlet(registry)), "/");

        // 配置 Jetty 安全约束
        configureJettySecurity(context);

        Server server = new Server(Integer.parseInt(databaseConfig.getPrometheusPort()));
        server.setHandler(context);
        server.start();
        return server;
    }

    /**
     * 配置 /metrics_now 的动态指标采集
     */
    private static ServletHolder createMetricsNowServlet(List<Collector> collectors) {
        return new ServletHolder(new MetricsServlet(CollectorRegistry.defaultRegistry)) {
            @Override
            public synchronized void handle(org.eclipse.jetty.server.Request baseRequest,
                                            javax.servlet.ServletRequest request,
                                            javax.servlet.ServletResponse response)
                    throws javax.servlet.ServletException, IOException {
                List<Thread> gatherNowThreads = collectors.stream()
                        .map(collector -> new Thread(() -> collector.collectNow(5000), "Gather NOW"))
                        .collect(Collectors.toList());

                gatherNowThreads.forEach(Thread::start);
                for (Thread t : gatherNowThreads) {
                    try {
                        t.join();
                    } catch (InterruptedException e) {
                        throw new IOException("收集指标时出错", e);
                    }
                }
                super.handle(baseRequest, request, response);
            }
        };
    }

    /**
     * 配置 Jetty 安全约束，禁止 TRACE 方法
     */
    private static void configureJettySecurity(ServletContextHandler context) {
        Constraint constraint = new Constraint();
        constraint.setName("No TRACE");
        // 不需要身份认证，只需禁用 TRACE 方法
        constraint.setAuthenticate(false);

        ConstraintMapping mapping = new ConstraintMapping();
        mapping.setPathSpec("/*");
        mapping.setMethod("TRACE");
        mapping.setConstraint(constraint);

        ConstraintSecurityHandler securityHandler = new ConstraintSecurityHandler();
        securityHandler.addConstraintMapping(mapping);
        context.setSecurityHandler(securityHandler);
    }

    /**
     * 处理异常
     */
    private static void handleException(Exception e, Server server, DbConfig databaseConfig) {
        if (e instanceof BindException) {
            log.error("端口号 {} 已被占用", databaseConfig.getPrometheusPort());
        } else {
            log.error("收集错误: {}", e.getMessage(), e);
        }

        if (server != null) {
            try {
                server.stop();
            } catch (Exception stopException) {
                log.error("关闭服务器时出错: {}", stopException.getMessage(), stopException);
            }
        }
    }
}