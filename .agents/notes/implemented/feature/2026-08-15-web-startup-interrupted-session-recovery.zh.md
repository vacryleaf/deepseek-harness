# Agent Note：Web 启动继续中断会话

Status: implemented

[English](2026-08-15-web-startup-interrupted-session-recovery.md) | 中文

## 问题

会话持久化会把打开的最后轮次修复为 reason 为 `interrupted` 的逻辑 `turn/end`。Web 后续请求可以冷恢复该会话，但进程重启后没有请求来唤醒恢复的 Agent。直接重放原始 prompt 会向模型历史追加重复用户输入，而空的普通 follow-up 不会进入模型循环。

## 决策

Agent 接口提供 `resumeInterruptedTurn()`。它要求 Agent 处于 idle，且忽略 `session/end-seed` 标记后，最后一个逻辑事件是中断轮次。循环会开启一个新的持久轮次，并只允许这次显式唤醒通过空的首个 inbox 领取。下一次模型请求从已有会话日志组装，因此该操作不添加普通用户消息。

`ApiProxyService` 提供仅供宿主使用的 `resumeInterruptedSessions()` 运行时辅助方法。启用 `resumeInterruptedSessions` 后，Web Gateway 会在启动时列出已持久化的项目会话，检查修复后的逻辑视图，并通过 `createApiRemoteAgentResolver()` 冷恢复每个符合条件的根会话。解析器会在发布前组装会话记录的 preset。实时标识、没有项目目录的会话、由子代理拥有的标识、已完成轮次以及最后结果不是 interrupted 的会话都会被排除。每个 Gateway 实例只保留一个恢复 promise，因此重复调用是幂等的；如果进程在恢复期间再次失败，留下的中断尾部会在下一次启动时重试同一历史。

通用 Gateway 配置的 `resumeInterruptedSessions` 默认值为 `false`；随发行版交付的 Web 组合将其设置为 `true`。扫描会触发符合条件的 Agent，但不等待其模型工作结束；轮次执行、提供方重试、取消和持久化所有权仍由 AgentLoop 与现有会话检查点策略负责。

## 考虑过的替代方案

- 拒绝重放最后一条用户 prompt，因为该 prompt 已经持久化，重放会让模型历史出现两次。
- 拒绝未记录的环境唤醒，因为恢复请求必须能够从会话日志重建。显式轮次边界和已有历史在不发明第二条 prompt 的前提下满足“模型可见即已记录”约束。
- 拒绝恢复所有持久化标识，因为子代理会话属于父委派生命周期。通用 Web 恢复只处理根会话。

## 影响

Web 进程重启后，可以继续持久尾部在模型请求或工具边界期间中断的任务。如果恢复轮次以错误结束，或用户曾主动取消轮次，后续启动不会自动重试；恢复动作仍是显式 prompt。关闭浏览器标签页不属于此功能，因为 Web Host 及其 Agent 仍在运行。动态上下文生产方可能在恢复轮次追加自己的面向模型上下文消息，但不会重复原始用户消息。

## 验证

- `packages/core/agent-loop/tests/resume.spec.ts` 验证中断日志只保留一条原始用户消息，并以新的已完成轮次继续。
- `packages/host/apiproxy/tests/api-proxy-cold.spec.ts` 验证根会话／中断状态筛选、子代理排除与触发幂等性。
- `apps/web/tests/smoke-real.e2e.ts` 使用同一个 `DSH_HOME` 启动两个真实 Web 进程，在第一个进程保持提供方请求期间终止它，并验证第二个进程通过本地提供方完成任务。
- [语义会话检查点](../bug-fix/2026-07-21-semantic-session-checkpoints.md)继续作为恢复所依赖的持久请求与工具边界证据的权威记录。
