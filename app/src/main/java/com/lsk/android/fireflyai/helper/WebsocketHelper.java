package com.lsk.android.fireflyai.helper;

import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.nio.ByteBuffer;
import java.util.concurrent.LinkedBlockingQueue;
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
    private WebSocket websocket;
    private Consumer<ByteBuffer> rawHandler;
    private Consumer<String> textHandler;
    private final String url;
    private final OkHttpClient client;
    private Thread networkThread;
    private AtomicBoolean isRunning = new AtomicBoolean(false);
    private AtomicBoolean isConnected = new AtomicBoolean(false);
    private AtomicBoolean shouldReconnect = new AtomicBoolean(true);
    private LinkedBlockingQueue<byte[]> rawDataQueue = new LinkedBlockingQueue<>();
    private LinkedBlockingQueue<String> textDataQueue = new LinkedBlockingQueue<>();

    public WebsocketHelper(String url) {
        this.url = url;
        this.client = new OkHttpClient();
    }

    private void connect() {
        Request request = new Request.Builder().url(url).build();
        this.websocket = client.newWebSocket(request, this);
        client.dispatcher().executorService().shutdown();
    }

    private void close() {
        this.shouldReconnect.set(false);
        this.websocket.close(1000, "Do not need connection anymore");
    }

    @Override
    public void onMessage(@NonNull WebSocket webSocket, @NonNull ByteString bytes) {
        super.onMessage(webSocket, bytes);
        this.rawHandler.accept(bytes.asByteBuffer());
    }

    @Override
    public void onMessage(@NonNull WebSocket webSocket, @NonNull String text) {
        super.onMessage(webSocket, text);
        this.textHandler.accept(text);
    }

    @Override
    public void onOpen(@NonNull WebSocket webSocket, @NonNull Response response) {
        super.onOpen(webSocket, response);
        this.isConnected.set(true);
    }

    @Override
    public void onFailure(@NonNull WebSocket webSocket, @NonNull Throwable t, @Nullable Response response) {
        super.onFailure(webSocket, t, response);
        Log.e(TAG, "onFailure: ws onFailure triggered", t);
    }

    @Override
    public void onClosed(@NonNull WebSocket webSocket, int code, @NonNull String reason) {
        super.onClosed(webSocket, code, reason);
        this.isConnected.set(false);
        if (this.shouldReconnect.get()) {
            connect();
        }
    }

    @Override
    public void run() {
        connect();
        while (isRunning.get()) {
            if (!this.isConnected.get()) { continue; }
            byte[] data = rawDataQueue.poll();
            if (data != null && data.length != 0) {
                websocket.send(new ByteString(data));
            }
            String text = textDataQueue.poll();
            if (text != null && !textDataQueue.isEmpty()) {
                websocket.send(text);
            }
        }
        close();
    }

    public void send(byte[] bytes) {
        if (this.isRunning.get()) {
            try {
                this.rawDataQueue.put(bytes);
            } catch (InterruptedException e) {
                Log.e(TAG, "send: interrupted", e);
            }
        }
    }
    
    public void send(String text) {
        if (this.isRunning.get()) {
            try {
                this.textDataQueue.put(text);
            } catch (InterruptedException e) {
                Log.e(TAG, "send: interrupted", e);
            }
        }
    }

    public void setRawHandler(Consumer<ByteBuffer> rawHandler) {
        this.rawHandler = rawHandler;
    }

    public void setTextHandler(Consumer<String> textHandler) {
        this.textHandler = textHandler;
    }

    public void start() {
        if (this.isRunning.get() && this.networkThread != null) return;
        this.isRunning.set(true);
        this.networkThread = new Thread(this, "Network Thread for " + url);
        this.networkThread.start();
    }

    public void stop() {
        if (!this.isRunning.get()) return;
        this.isRunning.set(false);
        if (this.networkThread != null) {
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
