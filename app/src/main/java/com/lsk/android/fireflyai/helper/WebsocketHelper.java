package com.lsk.android.fireflyai.helper;

import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.nio.ByteBuffer;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;
import okio.ByteString;

public class WebsocketHelper extends WebSocketListener implements Runnable {
    private static final String TAG = "WebsocketHelper";
    private static final long RECONNECT_DELAY_MS = 3000;
    private static final int MAX_QUEUE_SIZE = 256;
    private WebSocket websocket;
    private Consumer<ByteBuffer> rawHandler;
    private Consumer<String> textHandler;
    private Consumer<String> statusHandler;
    private final String url;
    private final OkHttpClient client;
    private Thread networkThread;
    private final AtomicBoolean isRunning = new AtomicBoolean(false);
    private final AtomicBoolean isConnected = new AtomicBoolean(false);
    private final AtomicBoolean shouldReconnect = new AtomicBoolean(true);
    private final LinkedBlockingQueue<byte[]> rawDataQueue = new LinkedBlockingQueue<>(MAX_QUEUE_SIZE);
    private final LinkedBlockingQueue<String> textDataQueue = new LinkedBlockingQueue<>(MAX_QUEUE_SIZE);

    public WebsocketHelper(String url) {
        this.url = url;
        this.client = new OkHttpClient();
    }

    private void connect() {
        try {
            Request request = new Request.Builder().url(url).build();
            this.websocket = client.newWebSocket(request, this);
        } catch (IllegalArgumentException e) {
            notifyStatus("Invalid WebSocket URL: " + url);
            Log.e(TAG, "connect: invalid URL " + url, e);
        }
    }

    private void close() {
        this.shouldReconnect.set(false);
        this.isConnected.set(false);
        WebSocket ws = this.websocket;
        if (ws != null) {
            ws.close(1000, "Do not need connection anymore");
        }
        this.websocket = null;
    }

    private void notifyStatus(String message) {
        if (statusHandler != null) {
            statusHandler.accept(message);
        }
    }

    @Override
    public void onMessage(@NonNull WebSocket webSocket, @NonNull ByteString bytes) {
        super.onMessage(webSocket, bytes);
        if (this.rawHandler != null) {
            this.rawHandler.accept(bytes.asByteBuffer());
        }
    }

    @Override
    public void onMessage(@NonNull WebSocket webSocket, @NonNull String text) {
        super.onMessage(webSocket, text);
        if (this.textHandler != null) {
            this.textHandler.accept(text);
        }
    }

    @Override
    public void onOpen(@NonNull WebSocket webSocket, @NonNull Response response) {
        super.onOpen(webSocket, response);
        this.websocket = webSocket;
        this.isConnected.set(true);
    }

    @Override
    public void onFailure(@NonNull WebSocket webSocket, @NonNull Throwable t, @Nullable Response response) {
        super.onFailure(webSocket, t, response);
        this.isConnected.set(false);
        this.websocket = null;
        if (this.isRunning.get() && this.shouldReconnect.get()) {
            String reason = t.getMessage() == null ? t.getClass().getSimpleName() : t.getMessage();
            notifyStatus("Connection failed: " + reason + ". Retrying...");
        }
        Log.e(TAG, "onFailure: ws onFailure triggered", t);
    }

    @Override
    public void onClosed(@NonNull WebSocket webSocket, int code, @NonNull String reason) {
        super.onClosed(webSocket, code, reason);
        this.isConnected.set(false);
        this.websocket = null;
        if (this.isRunning.get() && this.shouldReconnect.get()) {
            notifyStatus("Connection closed: " + reason + ". Retrying...");
        }
    }

    @Override
    public void run() {
        while (isRunning.get()) {
            try {
                if (!this.isConnected.get()) {
                    connect();
                    Thread.sleep(RECONNECT_DELAY_MS);
                    continue;
                }

                WebSocket ws = this.websocket;
                if (ws == null) {
                    this.isConnected.set(false);
                    continue;
                }

                byte[] data = rawDataQueue.poll(200, TimeUnit.MILLISECONDS);
                if (data != null && data.length != 0) {
                    ws.send(new ByteString(data));
                }
                String text = textDataQueue.poll();
                if (text != null) {
                    ws.send(text);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                this.isConnected.set(false);
                notifyStatus("Connection error: " + e.getMessage() + ". Retrying...");
                Log.e(TAG, "run: websocket loop failed", e);
            }
        }
        close();
    }

    public void send(byte[] bytes) {
        if (this.isRunning.get()) {
            if (!this.rawDataQueue.offer(bytes)) {
                this.rawDataQueue.poll();
                this.rawDataQueue.offer(bytes);
            }
        }
    }
    
    public void send(String text) {
        if (this.isRunning.get()) {
            if (!this.textDataQueue.offer(text)) {
                this.textDataQueue.poll();
                this.textDataQueue.offer(text);
            }
        }
    }

    public void setRawHandler(Consumer<ByteBuffer> rawHandler) {
        this.rawHandler = rawHandler;
    }

    public void setTextHandler(Consumer<String> textHandler) {
        this.textHandler = textHandler;
    }

    public void setStatusHandler(Consumer<String> statusHandler) {
        this.statusHandler = statusHandler;
    }

    public void start() {
        if (this.isRunning.get() && this.networkThread != null) return;
        this.shouldReconnect.set(true);
        this.isRunning.set(true);
        this.networkThread = new Thread(this, "Network Thread for " + url);
        this.networkThread.start();
    }

    public void stop() {
        if (!this.isRunning.get()) return;
        this.isRunning.set(false);
        this.shouldReconnect.set(false);
        close();
        if (this.networkThread != null) {
            this.networkThread.interrupt();
            try {
                this.networkThread.join();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                this.networkThread = null;
                this.rawDataQueue.clear();
                this.textDataQueue.clear();
            }
        }

    }


}
