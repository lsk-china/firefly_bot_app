package com.lsk.android.fireflyai.helper;

import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.nio.ByteBuffer;
import java.util.function.Consumer;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;
import okio.ByteString;

public class WebsocketHelper extends WebSocketListener {
    private static final String TAG = "WebsocketHelper";
    private WebSocket websocket;
    private Consumer<ByteBuffer> rawHandler;
    private Consumer<String> textHandler;
    private boolean connected = false;
    private boolean reconnect = true;
    private final String url;
    private final OkHttpClient client;

    public void setRawHandler(Consumer<ByteBuffer> rawHandler) {
        this.rawHandler = rawHandler;
    }

    public void setTextHandler(Consumer<String> textHandler) {
        this.textHandler = textHandler;
    }

    public WebsocketHelper(String url) {
        this.url = url;
        this.client = new OkHttpClient();
        connect();
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
        this.connected = true;
    }

    @Override
    public void onFailure(@NonNull WebSocket webSocket, @NonNull Throwable t, @Nullable Response response) {
        super.onFailure(webSocket, t, response);
        Log.e(TAG, "onFailure: ws onFailure triggered", t);
    }

    @Override
    public void onClosed(@NonNull WebSocket webSocket, int code, @NonNull String reason) {
        super.onClosed(webSocket, code, reason);
        if (this.reconnect) {
            connect();
        }

    }

    public void send(byte[] bytes) {
        if (!this.connected) {
            Log.e(TAG, "send: ws is not opened");
            return;
        }
        this.websocket.send(new ByteString(bytes));
    }
    
    public void send(String text) {
        if (!this.connected) {
            Log.e(TAG, "send: ws is not connected");
            return;
        }
        this.websocket.send(text);
    }

    public void connect() {
        Request request = new Request.Builder().url(url).build();
        this.websocket = client.newWebSocket(request, this);
        client.dispatcher().executorService().shutdown();
    }

    public void close() {
        this.reconnect = false;
        this.websocket.close(1000, "Do not need connection anymore");
    }
}
