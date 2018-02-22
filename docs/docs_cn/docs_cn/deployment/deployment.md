# Deployment

本节介绍部署爱马仕的基本操作方面。 有关配置Hermes的更多信息，请阅读：

* [how to connect to Kafka and Zookeeper](/configuration/kafka-and-zookeeper)
* [how to fine tune Frontend](/configuration/frontend-tuning)
* [how to fine tune Consumers](/configuration/consumers-tuning)
* [how to configure Console](/configuration/console)
* [how to publish metrics](/configuration/metrics)

## Dependencies

As the [architecture overview](/overview/architecture) states, there are two systems that are required to run
Hermes:

* **Kafka**
* **Zookeeper**

在我们看来，最好的做法是在不同的主机上运行它们，所以爱马仕不会影响它们。

## Scalability

每个模块都是无状态的应用程序。 根据需要，可以有多个并行运行。 为了最好性能和维护方便，每个爱马仕模块还应该部署在单独的主机上。

## Requirements

所有Hermes Java模块都需要** Java 8 **才能工作。 爱马仕控制台没有外部依赖。

## Passing environment variables
所有Java模块共享相同的捆绑策略：Gradle distZips。 为了将任何命令行选项传递给可执行文件，请使用：

* `HERMES_<module name>_OPTS` for application options
* `JAVA_OPTS` for Java specific options

for example:

```
bash
export HERMES_FRONTEND_OPTS="-Dfrontend.port=8090"
export JAVA_OPTS="-Xmx2g"
```

## Frontend and Consumers

### External configuration

爱马仕前端和消费者模块使用[Netflix Archaius]（https://github.com/Netflix/archaius/）来管理配置。
要从任何URL（本地文件或远程HTTP源）读取外部配置，请在系统属性中指定其位置：

```
bash
export HERMES_FRONTEND_OPTS="-Darchaius.configurationSource.additionalUrls=file:///opt/hermes/conf/frontend.properties"
export HERMES_CONSUMERS_OPTS="-Darchaius.configurationSource.additionalUrls=file:///opt/hermes/conf/consumers.properties"
```

配置以Java属性格式存储。

### Overwriting configuration using ENV

可以使用环境变量覆盖任何配置变量：

```
bash
export HERMES_FRONTEND_OPTS="-D<configuration-option>=<value>"
```

for example:

```bash
export HERMES_FRONTEND_OPTS="-Dfrontend.port=8090 -Dfrontend.idle.timeout=30"
```

### Java options

建议使用G1垃圾收集器运行爱马仕前端和消费者，至少1GB堆：

```
-XX:+UseG1GC -Xms1g
```

## Management

### External configuration

管理是Spring Boot应用程序，共享相同的选项以提供额外的配置。 提供外部配置文件的最基本的方法是导出一个环境变量：

```
SPRING_CONFIG_LOCATION="file:///opt/hermes/conf/management.properties"
```

### Overwriting configuration using ENV

```
bash
export HERMES_MANAGEMENT_OPTS="-D<configuration-option>=<value>"
```

```
bash
export HERMES_MANAGEMENT_OPTS="-Dserver.port=8070"
```

## Console

爱马仕控制台是一个使用NodeJS服务的简单单页面应用程序。 它接受两个参数：

* `-p`或`HERMES_CONSOLE_PORT` env变量来指定端口（默认值：8000）
* `-c`或`HERMES_CONSOLE_CONFIG` env变量来指定配置文件（默认值：`./config.json`）

`config.json`文件是强制性的，Hermes控制台在无法读取时会崩溃。
See [configuring Hermes Console](/configuration/console) （/配置/控制台）部分。

Hermes控制台没有依赖关系，并且在Linux机器上运行在容器之外。 要运行它，请使用提供的脚本：

```
./run.sh -p 8000 -c /etc/hermes-console/config.json
```
