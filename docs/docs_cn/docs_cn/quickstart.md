# Quickstart

这份10分钟的指南将向您展示如何运行整个爱马仕环境，创建话题和订阅以及发布一些消息。

## Prerequisities

为了阅读本教程，您需要：:

* [Vagrant 1.7.3+](https://www.vagrantup.com/)
* [VirtualBox](https://www.virtualbox.org/) (4.0.x, 4.1.x, 4.2.x, 4.3.x, 5.0.x)
* curl
* some running receiver service (in this guide we'll use [RequestBin](http://requestb.in))

## Setting up an environment

如[体系结构]（/概述/体系结构）部分所述，爱马仕包含多个模块，并要求运行Kafka和Zookeeper。 为了简单起见，我们准备了一个Vagrant文件。

bash
git clone https://github.com/allegro/hermes.git
cd hermes
vagrant up
```

如果你想运行特定版本的爱马仕，只需签出一个标签：

```bash
git checkout hermes-{version}
```

## 检查设置

如果系统正在运行，则在浏览器中访问Vagrant公共IP时应该看到爱马仕控制台。 参阅[http://10.10.10.10/](http://10.10.10.10/).

## Creating group and topic

现在您已准备好创建**主题**来发布消息。

在爱马仕的信息发布在聚合成**组**的主题上。因此，您需要先创建一个组，然后将其命名为“com.example.events”。

* 打开 [Hermes Console](http://10.10.10.10/#/groups)
* 点击蓝色加号按钮
* 输入组名称：`com.example.events`
* 所有其他信息是必需的，但现在只需输入任何内容

此时，您应该在组列表中看到您的组。 现在，让我们为我们的小组添加新的“点击”主题：

* 点击群组标题（[直接链接到com.example.events群组]（http://10.10.10.10/#/groups/com.example.events））
* 点击蓝色加号按钮
* 输入主题名称：`点击`
* 输入一些说明
* 将内容类型更改为JSON - 为简单起见，我们不想添加AVRO架构

## 发布和接收消息
要接收发布在主题上的消息，您必须创建**订阅**。 这是你告诉爱马仕在哪里发送关于主题发布的消息的地方。 您可以在单个主题上进行多次订阅（特别是 - none）

所以我们来创建一个`clicks-receiver`订阅：

* 点击主题标题（[直接链接到com.example.events.clicks组]（http://10.10.10.10/#/groups/com.example.events/topics/com.example.events.clicks））
* 点击蓝色加号按钮
* 输入订阅名称：`clicks-receiver`
* 设置消息将被发送到的端点，在这个例子中，我们可以使用http：// requestb.in / 1isy54g1`
* 输入一些描述和联系人数据

现在是时候举行盛大的结局。 让我们在我们的主题上发布消息（请注意，默认的Hermes发布端口是`8080`）：

```bash
curl -v -d '{"id": 12345, "page": "main"}' http://10.10.10.10:8080/topics/com.example.events.clicks

< HTTP/1.1 201 Created
< Hermes-Message-Id: 66feaead-0685-491e-9c87-00f940ead2c9
< Content-Length: 0
< Date: Mon, 04 May 2015 02:18:23 GMT
```

（第一次发布您可能会看到的内容408请求超时状态：许多机器需要预热，只需重试）

恭喜！ 该消息应该传递给您的服务或通过例如通过`http：//requestb.in/1isy54g1 inspect`## 来停止系统

停止虚拟机运行：

```bash
vagrant halt
```

再次运行它:

```bash
vagrant up
```

用以下方式销毁VM：

```bash
vagrant destroy
```
