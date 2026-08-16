package ai.deepseek.dsh.android

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.util.UUID
import java.util.concurrent.TimeUnit

/** Keeps a native DSH event connection at foreground-service priority. */
class DshKeepAliveService : Service() {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val httpClient = OkHttpClient.Builder()
        .pingInterval(30, TimeUnit.SECONDS)
        .build()
    private val sockets = linkedSetOf<WebSocket>()
    private val rootSessionIds = linkedSetOf<String>()
    private val seenCompletions = LinkedHashSet<String>()
    private var sessionListCall: Call? = null
    private var reconnectTask: Runnable? = null
    private var reconnectDelayMs = INITIAL_RECONNECT_DELAY_MS
    private var connectionGeneration = 0L
    private var serviceUrl: String? = null

    private val notifier by lazy { TaskCompletionNotifier(this) }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, buildNotification())
        val requestedUrl = intent?.getStringExtra(EXTRA_SERVICE_URL)
            ?: ServiceStore(getSharedPreferences(ServiceStore.PREFERENCES, MODE_PRIVATE)).last()
        if (requestedUrl.isNullOrBlank()) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (requestedUrl != serviceUrl || sockets.isEmpty()) connect(requestedUrl)
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        connectionGeneration += 1
        reconnectTask?.let(mainHandler::removeCallbacks)
        reconnectTask = null
        sessionListCall?.cancel()
        sessionListCall = null
        closeSockets()
        httpClient.connectionPool.evictAll()
        httpClient.dispatcher.executorService.shutdown()
        super.onDestroy()
    }

    private fun connect(origin: String) {
        connectionGeneration += 1
        val generation = connectionGeneration
        serviceUrl = origin
        reconnectTask?.let(mainHandler::removeCallbacks)
        reconnectTask = null
        sessionListCall?.cancel()
        sessionListCall = null
        closeSockets()
        rootSessionIds.clear()
        refreshRootSessions(origin, generation)
    }

    private fun refreshRootSessions(origin: String, generation: Long) {
        val request = Request.Builder()
            .url(httpUrl(origin, "/api/session.list"))
            .post(
                JSONObject()
                    .put("type", "client-request")
                    .put("rpcId", UUID.randomUUID().toString())
                    .put("method", "session.list")
                    .put("payload", JSONObject())
                    .toString()
                    .toRequestBody(JSON_MEDIA_TYPE),
            )
            .build()
        sessionListCall = httpClient.newCall(request).also { call ->
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, error: IOException) {
                    mainHandler.post { scheduleReconnect(generation) }
                }

                override fun onResponse(call: Call, response: Response) {
                    val roots = response.use {
                        if (!it.isSuccessful) null else it.body?.string()?.let(::parseRootSessionIds)
                    }
                    mainHandler.post {
                        if (generation != connectionGeneration) return@post
                        sessionListCall = null
                        if (roots == null) {
                            scheduleReconnect(generation)
                            return@post
                        }
                        rootSessionIds.clear()
                        rootSessionIds.addAll(roots)
                        reconnectDelayMs = INITIAL_RECONNECT_DELAY_MS
                        openDownlink(origin, "/api/events.mux", generation, isHost = false)
                        openDownlink(origin, "/api/events.host", generation, isHost = true)
                    }
                }
            })
        }
    }

    private fun openDownlink(origin: String, path: String, generation: Long, isHost: Boolean) {
        val request = Request.Builder().url(webSocketUrl(origin, path)).build()
        val socket = httpClient.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                mainHandler.post {
                    if (generation == connectionGeneration) reconnectDelayMs = INITIAL_RECONNECT_DELAY_MS
                }
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                mainHandler.post {
                    if (generation == connectionGeneration) handleFrame(text, isHost)
                }
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                mainHandler.post {
                    sockets.remove(webSocket)
                    if (generation == connectionGeneration) scheduleReconnect(generation)
                }
            }

            override fun onFailure(webSocket: WebSocket, error: Throwable, response: Response?) {
                mainHandler.post {
                    sockets.remove(webSocket)
                    if (generation == connectionGeneration) scheduleReconnect(generation)
                }
            }
        })
        sockets.add(socket)
    }

    private fun handleFrame(text: String, isHost: Boolean) {
        if (isHost) {
            val change = parseRootSessionChange(text) ?: return
            if (change.added) rootSessionIds.add(change.sessionId)
            else rootSessionIds.remove(change.sessionId)
            return
        }
        val completion = parseNativeTaskCompletion(text, rootSessionIds) ?: return
        val origin = serviceUrl ?: return
        val key = listOf(origin, completion.sessionId, completion.turn, completion.sequence).joinToString("|")
        if (!rememberCompletion(key)) return
        notifier.notifyTaskCompleted(origin, completion)
    }

    private fun rememberCompletion(key: String): Boolean {
        if (!seenCompletions.add(key)) return false
        while (seenCompletions.size > MAX_SEEN_COMPLETIONS) {
            seenCompletions.iterator().next().let(seenCompletions::remove)
        }
        return true
    }

    private fun scheduleReconnect(generation: Long) {
        if (generation != connectionGeneration || reconnectTask != null || serviceUrl == null) return
        closeSockets()
        sessionListCall?.cancel()
        sessionListCall = null
        val delay = reconnectDelayMs
        reconnectDelayMs = (reconnectDelayMs * 2).coerceAtMost(MAX_RECONNECT_DELAY_MS)
        val task = Runnable {
            reconnectTask = null
            serviceUrl?.let(::connect)
        }
        reconnectTask = task
        mainHandler.postDelayed(task, delay)
    }

    private fun closeSockets() {
        sockets.toList().forEach { it.close(NORMAL_CLOSE_CODE, "reconnecting") }
        sockets.clear()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(
                KEEP_ALIVE_CHANNEL_ID,
                getString(R.string.keep_alive_notification_channel),
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = getString(R.string.keep_alive_notification_channel_description)
            },
        )
    }

    private fun buildNotification(): Notification {
        val launchIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val contentIntent = PendingIntent.getActivity(
            this,
            REQUEST_CODE,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return Notification.Builder(this, KEEP_ALIVE_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentTitle(getString(R.string.keep_alive_notification_title))
            .setContentText(getString(R.string.keep_alive_notification_message))
            .setContentIntent(contentIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setVisibility(Notification.VISIBILITY_PRIVATE)
            .build()
    }

    companion object {
        private const val EXTRA_SERVICE_URL = "service_url"
        private const val KEEP_ALIVE_CHANNEL_ID = "dsh-keep-alive"
        private const val NOTIFICATION_ID = 3101
        private const val REQUEST_CODE = 3102
        private const val NORMAL_CLOSE_CODE = 1000
        private const val INITIAL_RECONNECT_DELAY_MS = 1_000L
        private const val MAX_RECONNECT_DELAY_MS = 60_000L
        private const val MAX_SEEN_COMPLETIONS = 512
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

        /** Starts native event monitoring while the Activity is visible. */
        fun start(context: Context, serviceUrl: String) {
            val intent = Intent(context, DshKeepAliveService::class.java)
                .putExtra(EXTRA_SERVICE_URL, serviceUrl)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        /** Stops native event monitoring when the user leaves the remote service. */
        fun stop(context: Context) {
            context.stopService(Intent(context, DshKeepAliveService::class.java))
        }

        private fun httpUrl(origin: String, path: String): String = origin.trimEnd('/') + path

        private fun webSocketUrl(origin: String, path: String): String {
            val scheme = when {
                origin.startsWith("https://") -> "wss://"
                origin.startsWith("http://") -> "ws://"
                else -> return origin.trimEnd('/') + path
            }
            return scheme + origin.substringAfter("://").trimEnd('/') + path
        }
    }
}
