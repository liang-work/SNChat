package org.dpdns.thsl.coralisland.snchat.service

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener

private const val TAG = "WebSocketManager"

interface WebSocketEventListener {
    fun onConnected() {}
    fun onMessage(text: String) {}
    fun onDisconnected(reason: String) {}
    fun onError(message: String) {}
}

class WebSocketManager {

    private var webSocket: WebSocket? = null
    private val httpClient = OkHttpClient()
    private var isConnected = false
    private var reconnectJob: Job? = null
    private var serverUrl: String = ""
    private var token: String = ""

    var listener: WebSocketEventListener? = null
    var onConnectionStateChanged: ((Boolean) -> Unit)? = null

    private val scope = CoroutineScope(Dispatchers.IO)

    fun connect(url: String, tokenStr: String) {
        if (isConnected) {
            Log.d(TAG, "Already connected")
            return
        }

        serverUrl = url
        token = tokenStr

        val wsUrl = url
            .replace("https://", "wss://")
            .replace("http://", "ws://")

        val fullUrl = "$wsUrl/messager/ws?token=$tokenStr"

        Log.d(TAG, "Connecting to WebSocket: $fullUrl")

        val request = Request.Builder()
            .url(fullUrl)
            .build()

        webSocket = httpClient.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.d(TAG, "WebSocket connected")
                isConnected = true
                reconnectJob?.cancel()
                onConnectionStateChanged?.invoke(true)
                listener?.onConnected()
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                Log.d(TAG, "WebSocket message: $text")
                listener?.onMessage(text)
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                Log.d(TAG, "WebSocket closing: $code $reason")
                webSocket.close(1000, null)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Log.d(TAG, "WebSocket closed: $code $reason")
                isConnected = false
                onConnectionStateChanged?.invoke(false)
                listener?.onDisconnected(reason)
                scheduleReconnect()
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.e(TAG, "WebSocket failure", t)
                isConnected = false
                onConnectionStateChanged?.invoke(false)
                listener?.onError(t.message ?: "Connection failed")
                scheduleReconnect()
            }
        })
    }

    fun disconnect() {
        reconnectJob?.cancel()
        webSocket?.close(1000, "User disconnected")
        webSocket = null
        isConnected = false
        onConnectionStateChanged?.invoke(false)
        Log.d(TAG, "WebSocket disconnected")
    }

    private fun scheduleReconnect() {
        if (serverUrl.isEmpty() || token.isEmpty()) return

        reconnectJob?.cancel()
        reconnectJob = scope.launch {
            Log.d(TAG, "Scheduling reconnect in 3 seconds...")
            delay(3000)
            if (!isConnected) {
                Log.d(TAG, "Attempting to reconnect...")
                connect(serverUrl, token)
            }
        }
    }

    fun sendMessage(message: String): Boolean {
        return webSocket?.send(message) ?: false
    }

    fun isConnected(): Boolean = isConnected
}