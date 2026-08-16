# Agent Note：通过 Tailscale 连接的 Android 远程客户端

Status: implemented

[English](2026-08-15-android-tailscale-remote-client.md) | 中文

> 范围：plugin/android-remote 中的原生 Android 壳层、远程 origin 策略，以及 DSH Web 宿主的私有网络部署方式。本决策不把 DSH 执行、凭据或工具提供方移入 Android。

## 问题

DSH Web UI 需要一个手机客户端，同时由宿主继续负责 Agent、会话、Shell、文件系统、子进程和 LSP 能力。把 Node 运行时嵌入移动包会重复宿主生命周期和平台相关工具实现。由 Tailnet 宿主提供的浏览器壳层可以复用现有 boot manifest、HTTP RPC、WebSocket 流、上传和重连行为，不需要引入第二套客户端协议。

## 决策

**Android 使用原生 WebView 作为远程客户端。** plugin/android-remote 收集并保存多个经过校验的 DSH origin，记住上次选择的 origin，并在受限 WebView 中加载完整的远程 Web 应用。该 App 不执行 Node、Shell、子进程、文件系统或 LSP 代码。

**使用 Tailscale Serve 部署。** DSH 继续绑定 127.0.0.1；Tailscale Serve 通过 HTTPS 将本地监听器发布到 Tailnet。Android WebView 直接加载 Tailscale HTTPS origin，因此页面、/api 请求和 WebSocket 事件路径保持同源。由于 API 信任栅栏会检查转发后的 authority，需要把 DSH 主机名加入 --trusted-host。

**客户端只接受 origin，不接受任意 URL。** ServiceUrlPolicy 拒绝路径、查询参数、片段、用户信息、缺少主机名和发布版 HTTP 地址。Debug 构建允许 HTTP 进行局域网测试；Release manifest 将 usesCleartextTraffic 设为 false。TLS 错误会取消连接，不会绕过证书校验；WebView 同时禁用文件访问和混合内容。

**Tailscale 访问不是应用认证协议。** Tailnet 成员身份和 ACL 控制网络可达性。Android 客户端不自行发明 bearer token 或登录协议；额外的应用认证属于远程 DSH 部署或其认证代理。现有 trustedHosts 检查仍然是浏览器信任栅栏，不是身份机制。

## 曾考虑的替代方案

**在 Android 内运行 DSH。** 不采用：当前运行时依赖 Node、子进程、本地文件系统语义和宿主插件，这些能力无法作为同一执行环境直接提供给 Android。

**打包第二套 React 或 Capacitor 传输客户端。** 第一版不采用：加载远程 Web origin 可以保留现有 boot 注入和连接实现，而本地 bundle 在增加用户能力之前还需要新的 boot 配置和跨来源协议路径。

**让 DSH 绑定所有网卡并连接局域网地址。** 不作为默认部署：让 DSH 保持 loopback、使用 Tailscale Serve，可以限制监听范围，并在不开放公网端口的情况下提供私有加密网络路径。

## 后果

手机需要运行 Tailscale，与 DSH 宿主加入同一 Tailnet，并通过 Tailnet ACL 获准访问。宿主运行带有 Tailscale HTTPS 主机名 trusted-host 配置的 dsh web，再将 tailscale serve 指向 loopback Web 端口。Android App 可以独立于宿主运行时构建和安装。

WebView 保存服务列表和上次选择的 origin，并提供原生设置页、切换服务、浏览器返回、外部链接处理、文件选择、重连、TLS 错误处理以及根会话完成后的本地通知。Android 13 及更新版本需要通知权限。服务加载期间，plugin/android-remote 启动低优先级 data-sync 前台服务，调用 session.list，监听现有 events.mux 和 events.host 下行流，过滤根会话、带退避重连并去重完成通知。WebView 回调仍是页面加载时的备用通道。这会降低常规后台回收概率，但不能覆盖强制停止、厂商省电策略或系统级终止，也不是 FCM 的替代品。未来仍可增加原生 RPC 载体，但必须保持远程执行边界和宿主拥有的能力模型。

客户端模块加载器会从每个插件 bundle 的内容计算 12 位 SHA-1 revision，并将其追加为 `?rev=...`；因此本地 runtime bundle 更新需要重新构建并重启宿主产物服务器，不需要手工修改 revision。发布包时还需要更新包版本和锁文件。实现与部署步骤位于 [Android README](../../../../plugin/android-remote/README.md)；传输与信任语义仍由 [WebSocket 下行载体说明](../architecture/2026-08-04-websocket-downlink-carrier.md) 和 webserver 宿主包负责。
