# Agent Note：交付的 provider profile 使用无限重试

状态：已实现

[English](2026-08-15-unbounded-provider-retries.md) | 中文

## 问题

交付的 Web 与 Headless profile 会挂载 `llm-retry`，但基础 DeepSeek 行没有提供方策略，因此 provider resolver 会提供有限的两次重试预算。API 地址发生间歇性中断时，即使后续请求可以成功，会话也可能因此结束。

## 决策

`dsh-base` 组合包为 DeepSeek 行配置 `retryPolicy.mode: always`。现有的有界指数退避保持不变，重试会持续到请求成功、会话被取消或所属运行时被 dispose。需要有限请求预算的后续 profile 或 home patch 仍可将该行替换为显式的 `normal` 策略。

## 备选方案

- **将 `dsh-llm` resolver 的默认值改为 `always`**——本次产品改动不采用，因为这会改变所有适配器和独立组合的行为，包括休眠的 `llm-pi-ai` 路由，以及有意依赖有限默认值的消费方。
- **使用很大的 `maxRetries` 值**——不采用，因为它仍然有限，会暴露误导性的数字预算，也不能表达由取消驱动的完成条件。
- **在每个 provider adapter 内部重试**——不采用，因为 `llm-retry` 已经负责失败步骤恢复、持久化重试事件、取消处理以及丢弃不完整流分片的保留。

## 影响

- 交付的 DeepSeek 路由会同时重试永久性 provider 错误和传输错误；需要快速失败的部署必须显式设置 `mode: normal`。
- 本地退避仍受 provider 默认值限制，因此长时间中断时尝试间隔不会快于配置的最大延迟，但请求次数不设上限。
- 每次重试都会发起新的 provider 请求，可能重复消耗输入 token 并产生费用；现有的 `llm/retry` 事件和 UI 继续展示这些尝试。

## 验证

- `packages/bundle/base/tests/base.spec.ts` 断言交付行解析出的 `retryPolicy.mode: always`。
- `apps/web/tests/smoke-real.e2e.ts` 启动交付的 Web 组合，注入三次传输错误，并要求第四次请求及其恢复响应成功。
