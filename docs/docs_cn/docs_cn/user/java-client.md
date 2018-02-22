# Java Client

一个精简的类库，旨在向爱马仕发布消息。

##特征

* http客户端不可知的API
*同步/异步发布
*可配置重试
*指标

## Overview

核心功能由`HermesClient`类提供，HermesClient使用`HermesSender`来完成繁重的工作。
目前有三种`HermesSender`实现：

* **RestTemplateHermesSender** - 建议基于 [Spring framework](http://projects.spring.io/spring-framework)构建的服务;
  使用 [AsyncRestTemplate](http://docs.spring.io/spring/docs/current/javadoc-api/org/springframework/web/client/AsyncRestTemplate.html)
  进行异步传输
* **JerseyHermesSender** - 建议使用  [Jersey](<https://jersey.java.net/>)
* **OkHttpHermesSender** - 支持 both HTTP/1.1 and HTTP/2 协议, 使用 [OkHttp3 client](http://square.github.io/okhttp/)


## Creating

要开始使用`爱马仕客户端`，将其添加为依赖项：

```groovy
compile group: 'pl.allegro.tech.hermes', name: 'hermes-client', version: versions.hermes
```

客户端应该始终使用`HermesClientBuilder`构建，它允许设置：

```java
HermesClient client = HermesClientBuilder.hermesClient(...)
    .withURI(...) // Hermes URI
    .withRetries(...) // how many times retry in case of errors, default: 3
    .withRetrySleep(...) // initial and max delay between consecutive retries in milliseconds, default: 100ms (initial), 300ms (max)
    .withDefaultContentType(...) // what Content-Type to use when none set, default: application/json
    .withDefaultHeaderValue(...) // append default headers added to each message
    .withMetrics(metricsRegistry) // see Metrics section below
    .build();
```

参阅 [Sender implementations](#sender-implementations) sections有关如何构建“HermesSender”的指南:

一旦创建，您就可以开始发布消息。 Hermes客户端API默认是异步的，返回包含结果承诺的`CompletableFuture`对象。

`HermesClient`公开了容易发布JSON和Avro消息的方法。

JSON Sender设置`application / json`内容类型。

```java
hermesClient.publishJSON("com.group.json", "{hello: 1}");
```

Avro sender sets `avro/binary` content type. It also requires to pass Avro schema version of this message, which is passed on to Hermes in `Schema-Version` header.

```java
hermesClient.publishAvro("com.group.avro", 1, avroMessage.getBytes());
```

You can also use `HermesMessage#Builder` to create HermesMessage object, to e.g. pass custom headers:

```java
hermesClient.publish(
    HermesMessage.hermesMessage("com.group.topic", "{hello: 1}")
        .json()
        .withHeader("My-Header", "header value")
        .build()
);
```

Publication reuslts in returning `HermesResponse` object:

```java
CompletableFuture<HermesResponse> result = client.publish("group.topic", "{}");

HermesResponse response = result.join();

assert response.isSuccess();
assert response.getStatusCode() == 201;
assert response.getMessageId().equals("..."); // message UUID generated by Hermes
```

## Closing

The client allows graceful shutdown, which causes it to stop accepting publish requests and await for delivery of currently processed messages.

Two variants of shutting down the client are available:

* synchronous `void close(long pollInterval, long timeout)` method will return when all currently processed messages
 (including retries) are delivered or discarded
* asynchronous `CompletableFuture<Void> closeAsync(long pollInterval)` returns immediately
 and the returned CompletableFuture completes when all messages are delivered or discarded 

`pollInterval` parameter is used for polling the internal counter of asynchronously processed messages with the user specified interval in milliseconds.

Calls to `publish` methods will return an exceptionally completed future with `HermesClientShutdownException`:
```java
client.close(50, 1000);

assert client.publish("group.topic", "{}").isCompletedExceptionally();
```

One can use the asynchronous method to wait for the client to terminate within a given timeout, e.g. in a shutdown hook:
```java
client.closeAsync(50).get(1, TimeUnit.SECONDS);
```

## Metrics

**Requirement**: dependency `io.dropwizard.metrics:metrics-core` must be provided at runtime.

After providing `MetricRegistry`, Hermes Client metrics will be reported to `hermes-client` branch. They include
measured latency, meters for each status code received and meter for failures.

```java
MetricRegistry registry = myMetricRegistryFactory.createMetricRegistry();

HermesClient client = HermesClientBuilder.hermesClient(sender)
    .withURI(URI.create("http://localhost:8080"))
    .withMetrics(registry)
    .build();
```

## Sender implementations

### Spring - AsyncRestTemplate

**Requirement**: `org.springframework:spring-web` must be provided at runtime.

```java
HermesClient client = HermesClientBuilder.hermesClient(new RestTemplateHermesSender(new AsyncRestTemplate()))
    .withURI(URI.create("http://localhost:8080"))
    .build();
```

### Jersey Client

**Requirement**: `org.glassfish.jersey.core:jersey-client` must be provided at runtime.

```java
HermesClient client = HermesClientBuilder.hermesClient(new JerseyHermesSender(ClientBuilder.newClient()))
    .withURI(URI.create("http://localhost:8080"))
    .build();
```

### OkHttp Client

Requirement: `com.squareup.okhttp3:okhttp` must be provided at runtime.

```java
HermesClient client = HermesClientBuilder.hermesClient(new OkHttpHermesSender(new OkHttpClient()))
    .withURI(URI.create("http://localhost:8080"))
    .build();
```

#### HTTP2 support

Requirements:

JVM configured with [ALPN support](http://www.eclipse.org/jetty/documentation/current/alpn-chapter.html#alpn-starting):

```bash
java -Xbootclasspath/p:<path_to_alpn_boot_jar> ...
```

OkHttp Client configured with [SSL support](https://github.com/square/okhttp/wiki/HTTPS):

```java
OkHttpClient client = new OkHttpClient.Builder()
        .sslSocketFactory(sslContext.getSocketFactory())
        .build();
        
HermesClient client = HermesClientBuilder.hermesClient(new OkHttpHermesSender(okHttpClient))
    .withURI(URI.create("https://localhost:8443"))
    .build();
```

### Custom HermesSender

Example with [Unirest](http://unirest.io/java.html) - very simple http client.

```java
HermesClient client = HermesClientBuilder.hermesClient((uri, message) -> {
    CompletableFuture<HermesResponse> future = new CompletableFuture<>();

    Unirest.post(uri.toString()).body(message.getBody()).asStringAsync(new Callback<String>() {
        @Override
        public void completed(HttpResponse<String> response) {
            future.complete(() -> response.getStatus());
        }

        @Override
        public void failed(UnirestException exception) {
            future.completeExceptionally(exception);
        }

        @Override
        public void cancelled() {
            future.cancel(true);
        }
    });

    return future;
})
.withURI(URI.create("http://localhost:8080"))
.build();
```