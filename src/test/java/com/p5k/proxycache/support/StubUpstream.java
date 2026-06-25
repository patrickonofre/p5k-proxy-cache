package com.p5k.proxycache.support;

import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Minimal in-process upstream stub backed by the JDK {@link HttpServer} (zero extra
 * dependencies). Records how many requests it received so tests can prove a cache HIT
 * made zero upstream calls.
 */
public class StubUpstream {

    private HttpServer server;
    private final AtomicInteger requestCount = new AtomicInteger();
    private volatile int status = 200;
    private volatile String contentType = "text/plain";
    private volatile String body = "";
    private volatile String lastPath;

    public void start() {
        try {
            server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        server.createContext("/", exchange -> {
            requestCount.incrementAndGet();
            lastPath = exchange.getRequestURI().getRawPath();
            byte[] payload = body.getBytes(StandardCharsets.UTF_8);
            if (contentType != null) {
                exchange.getResponseHeaders().add("Content-Type", contentType);
            }
            exchange.sendResponseHeaders(status, payload.length == 0 ? -1 : payload.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(payload);
            }
        });
        server.start();
    }

    public void respond(int status, String contentType, String body) {
        this.status = status;
        this.contentType = contentType;
        this.body = body;
    }

    public String baseUrl() {
        return "http://localhost:" + server.getAddress().getPort();
    }

    public int requestCount() {
        return requestCount.get();
    }

    public String lastPath() {
        return lastPath;
    }

    public void resetCount() {
        requestCount.set(0);
    }

    public synchronized void stopQuietly() {
        if (server != null) {
            server.stop(0);
            server = null;
        }
    }
}
