package com.binance.connector.futures.client.utils;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class WebSocketConnection extends WebSocketListener {
    private static final AtomicInteger connectionCounter = new AtomicInteger(0);
    private static final int NORMAL_CLOSURE_STATUS = 1000;
    private static final OkHttpClient client = HttpClientSingleton.getHttpClient();
    private static final Logger logger = LoggerFactory.getLogger(WebSocketConnection.class);

    private final WebSocketCallback onOpenCallback;
    private final WebSocketCallback onMessageCallback;
    private final WebSocketCallback onClosingCallback;
    private final WebSocketCallback onFailureCallback;
    private final int connectionId;
    private final Request request;
    private final String streamName;
    long lastReceivedTime = System.currentTimeMillis();
    private WebSocket webSocket;

    private final Object mutex;

    public long getLastReceivedTime() {
        return lastReceivedTime;
    }

    public WebSocketConnection(WebSocketCallback onOpenCallback, WebSocketCallback onMessageCallback,
                               WebSocketCallback onClosingCallback, WebSocketCallback onFailureCallback,
                               Request request) {
        this.onOpenCallback = onOpenCallback;
        this.onMessageCallback = onMessageCallback;
        this.onClosingCallback = onClosingCallback;
        this.onFailureCallback = onFailureCallback;
        this.connectionId = WebSocketConnection.connectionCounter.incrementAndGet();
        this.request = request;
        this.streamName = request.url().host() + request.url().encodedQuery();
        this.webSocket = null;
        this.mutex = new Object();
    }


    public void connect() {
        synchronized (mutex) {
            if (null == webSocket) {
                logger.info("[Connection {}] Connecting to {}", connectionId, streamName);
                webSocket = client.newWebSocket(request, this);
            } else {
                logger.info("[Connection {}] is already connected to {}", connectionId, streamName);
            }
        }
    }

    public int getConnectionId() {
        return connectionId;
    }

    public String getStreamName() {
        return streamName;
    }

    public void close() {
        if (null != webSocket) {
            logger.info("[Connection {}] Closing connection to {}", connectionId, streamName);
            webSocket.close(NORMAL_CLOSURE_STATUS, null);
            webSocket = null;
        }
    }

    @Override
    public void onOpen(WebSocket ws, Response response) {
        logger.info("[Connection {}] Connected to Server", connectionId);
        onOpenCallback.onReceive(streamName);
    }

    @Override
    public void onClosing(WebSocket ws, int code, String reason) {
        super.onClosing(ws, code, reason);
        onClosingCallback.onReceive("%s closing due to %s".formatted(streamName, reason));
    }

    @Override
    public void onMessage(WebSocket ws, String text) {
        onMessageCallback.onReceive(text);
        lastReceivedTime = System.currentTimeMillis();
        logger.debug("[Connection {}] Received message: {}, {}", streamName, text, Instant.ofEpochMilli(lastReceivedTime));
    }

    @Override
    public void onFailure(WebSocket ws, Throwable t, Response response) {
        logger.error("[Connection {}] Failure : {}", connectionId,response, t);
        onFailureCallback.onReceive(streamName);
    }
}
