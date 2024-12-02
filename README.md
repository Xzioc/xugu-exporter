# **xugu-exporter**

![构建状态](https://img.shields.io/badge/build-passing-brightgreen.svg)  
一个用于监控 Xugu 数据库性能指标的轻量级 Exporter。

---

## **目录**
- [项目简介](#项目简介)
- [功能特点](#功能特点)
- [安装教程](#安装教程)
- [使用说明](#使用说明)
- [配置指南](#配置指南)
- [许可协议](#许可协议)
- [参与贡献](#参与贡献)

---

## **项目简介**
`xugu-exporter` 是一个基于 Java 的监控工具，可将虚谷数据库的性能指标导出至 Prometheus，用于监控和告警。  
通过它可以实时跟踪数据库性能并轻松进行系统健康检查。

---

## **功能特点**
- 🖥️ **支持 Prometheus**：将 虚谷数据库的指标导出至 Prometheus 进行监控。
- ⚡ **高性能**：设计上尽量减少系统资源开销。
- 📊 **自定义指标**：支持轻松添加自定义数据库监控指标。
- 🔧 **灵活配置**：支持通过环境变量或配置文件进行灵活配置。

---

## **安装教程**

### **环境要求**
- **JDK 1.8 或更高版本**
- **Maven 3.6.5**
- **虚谷数据库 v12.3**
- **Prometheus**（可选，用于监控）

### **克隆项目代码**
```bash
git clone https://github.com/Xzioc/xugu-exporter.git
cd xugu-exporter
```

## **构建项目**
使用 Maven 构建项目并打包成 JAR 文件：
```bash
mvn clean package
```
此命令将生成一个可执行的 JAR 文件，位于 ```target/``` 目录下，文件名为 ```xugu-exporter.jar```
## **使用说明**
1. 运行生成的 JAR 文件：
   ​使用以下命令启动 ```xugu-exporter```：
```bash
java -jar target/xugu-exporter.jar
```
​		默认情况下，Exporter 会运行在 `8080` 端口。可以通过以下 URL 访问监控指标：

2. 将```resource```目录下的```collect.json```、```dbConfig.properties```文件与```xugu-exporter.jar```放置于同一文件目录下

3. 在 Prometheus 配置文件 ```prometheus.yml ```中添加以下内容来采集指标：

```yaml
scrape_configs:
  - job_name: 'xugu-exporter'
    static_configs:
      - targets: ['localhost:5138']

```
## **配置指南**

您可以通过以下方式进行配置：

### 通过配置文件 config.properties 进行连接配置：

| 配置项            | 默认值    | 描述                                 |
|-------------------|-----------|--------------------------------------|
| `jdbc.url`        | localhost | 数据库连接 URL                       |
| `jdbc.username`   | SYSDBA    | 数据库用户名                         |
| `jdbc.password`   | SYSDBA    | 数据库密码                           |
| `jdbc.dbname`     | SYSTEM    | 数据库名称                           |
| `jdbc.port`       | 5138      | 数据库端口                           |
| `jdbc.driver`     | com.xugu.cloudjdbc.Driver | 数据库 JDBC 驱动类名       |
| `prometheus.port` | 8080      | Prometheus 服务监听端口             |

### 通过配置文件 collect.json 进行收集配置：
| 字段名   | 说明                                            |
| -------- | ----------------------------------------------- |
| name     | 查询的名称                                      |
| remark   | 查询的描述或备注                                |
| interval | 查询执行的时间间隔（单位：秒）                  |
| enabled  | 查询是否启用，`true` 表示启用，`false` 表示禁用 |
| sql      | 执行的 SQL 查询语句                             |
| prefix   | 指定的前缀                                      |


## **许可协议**

本项目采用 [MIT 许可证](https://opensource.org/licenses/MIT) 开源。

## **参与贡献**

欢迎贡献代码！
如果你有建议或发现 bug，请创建一个 Issue 或者 Pull Request。
感谢你的参与！
