# DSH Android 远程客户端

[English](README.md) | 中文

这是一个加载远程 DSH Web 应用的原生 Android WebView 壳层。Android 进程不运行 Node、Shell、子进程、文件系统或 LSP 插件；这些能力仍在远程 DSH 主机上执行。

## 构建

在 Android Studio 中打开 plugin/android-remote，或在已生成 Gradle Wrapper 后运行 gradlew.bat :app:assembleDebug。Debug APK 输出到 app/build/outputs/apk/debug/app-debug.apk。Debug 构建允许使用 HTTP 地址进行局域网测试；Release 构建只接受 HTTPS 地址，当前 Release 输出未签名，分发前需要配置签名。

## App 行为

设置页可以录入并保存多个经过校验的服务 origin。选择已保存服务会将其设为上次使用的服务，下次启动 App 时自动打开该地址。旧版本的单服务 service_url 偏好会迁移到服务列表。

Android 13 及更新版本需要通知权限。服务加载期间，App 会启动低优先级的 `dataSync` 前台服务。该服务调用 `/api/session.list`，监听 `/api/events.mux` 和 `/api/events.host`，过滤根会话、带退避重连，并为每个完成的根任务发布原生通知。这是尽力而为的保活，不是 Android 的绝对保证：强制停止、厂商省电策略和系统级进程终止仍可能中断通知。这不是 FCM。远程宿主必须提供现有 HTTP API 和两个 WebSocket 路径。

## 更新 Web runtime

在仓库根目录运行 `pnpm run build`。构建会更新 `packages/client/runtime/lib/client.js` 和其他客户端 bundle。Web 模块加载器会计算 12 位 SHA-1 内容 revision，并将其写入 `?rev=...`，因此本地部署不需要手工修改 runtime 版本号。重启实际提供这些构建产物的 DSH Web 安装；如果 Windows 任务仍指向旧的全局 `dsh.cmd`，需要先更新或重新安装该运行时再重启。

只有发布新的 npm 包版本时，才需要修改 `packages/client/runtime/package.json` 并刷新 `pnpm-lock.yaml`。本地 Web 部署需要重新构建客户端 bundle 并重启宿主，单独修改包版本不会更新运行代码。

## Tailscale 部署

保持 DSH 监听回环地址，并使用 Tailscale Serve 将它发布到 Tailnet。例如，使用 Tailscale HTTPS 主机名启动 DSH 并代理本地 Web 服务：

    pnpm dsh web --port 3080 --trusted-host <machine>.<tailnet>.ts.net
    tailscale serve --bg http://127.0.0.1:3080

在 Android App 中打开 tailscale serve 显示的 HTTPS 主机名。手机和 DSH 主机必须属于同一个 Tailnet，且 Tailnet ACL 必须允许手机访问该主机。Tailscale 提供私有加密网络路径；DSH trustedHosts 只保护浏览器信任检查，不是身份认证。

## 远程主机要求

配置的地址必须提供完整的 DSH Web 应用，包括 /api 和两个 WebSocket 事件路径。使用 HTTPS/WSS。实现直接加载远程 Web origin，因此页面请求保持同源，并继续使用现有 DSH Web 启动清单、HTTP RPC、WebSocket 流、上传和重连行为。

Android 完成通知使用现有 DSH HTTP 和 WebSocket 事件路径，因此原生通道不要求增加 runtime bundle 版本。

不要将回环服务直接暴露到公网。如果服务部署在私有 Tailnet 之外，应先在其前面配置带认证的 HTTPS 反向代理。
