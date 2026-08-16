# 重试策略

[English](README.md) | 中文

这个 bundle 提供两种可以独立运行在 master 源码树上的重试功能：直接 DeepSeek 的无限重试，以及 settings-backed Pi AI 默认重试策略。

## 直接 DeepSeek

这个 bundle 替换 llm-deepseek 配置项，将 retryPolicy 设置为 mode always。它依赖已挂载 dsh-llm-retry 的标准 dsh-base profile，不会实现另一套重试循环。

## Pi AI 默认策略

这个 bundle 挂载 settings consumer，读取 llm-pi-ai 命名空间，为没有显式策略的每个 provider profile 添加 retryPolicy mode always。settings 变化时会再次协调，因此从 Models 页面新增的 provider 也会获得相同默认策略。已有显式策略会被保留。

策略由标准 settings provider 校验和持久化。现有 dsh-llm-retry plugin 继续负责 backoff、取消、session 事件和释放处理。显式的 provider 策略兼容 master。

## 安装

将它安装到已挂载 llm-pi-ai 和 llm-retry 的 profile 中：

    dsh plugin --profile <profile> add ./plugin/retry-always

只在静态 cordis entry 中配置的 provider 必须自行声明 retryPolicy。移除 bundle 不会删除已经持久化的策略；恢复原行为时需要显式删除这些 settings。
