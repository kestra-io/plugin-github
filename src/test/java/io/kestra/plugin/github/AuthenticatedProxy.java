package io.kestra.plugin.github;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * In-process forward proxy answering 407 until the expected Basic credentials arrive, then relaying to the target.
 */
public class AuthenticatedProxy implements AutoCloseable {
    private static final Set<String> SKIPPED_HEADERS = Set.of("host", "connection", "content-length", "expect", "upgrade", "proxy-authorization", "proxy-connection", "transfer-encoding");

    private final HttpServer server;
    private final String expectedAuthorization;
    private final HttpClient forwarder = HttpClient.newHttpClient();
    public final AtomicInteger rejected = new AtomicInteger();
    public final List<String> authenticatedUris = new CopyOnWriteArrayList<>();

    public AuthenticatedProxy(String username, String password) throws IOException {
        this.expectedAuthorization = "Basic " + Base64.getEncoder().encodeToString((username + ":" + password).getBytes(StandardCharsets.UTF_8));
        this.server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        this.server.createContext("/", this::handle);
        this.server.start();
    }

    public int port() {
        return server.getAddress().getPort();
    }

    private void handle(HttpExchange exchange) throws IOException {
        var body = exchange.getRequestBody().readAllBytes();
        if (!expectedAuthorization.equals(exchange.getRequestHeaders().getFirst("Proxy-Authorization"))) {
            rejected.incrementAndGet();
            exchange.getResponseHeaders().add("Proxy-Authenticate", "Basic realm=\"proxy\"");
            exchange.sendResponseHeaders(407, -1);
            exchange.close();
            return;
        }

        var target = exchange.getRequestURI();
        authenticatedUris.add(target.toString());
        try {
            var builder = HttpRequest.newBuilder(URI.create(target.toString()))
                .method(exchange.getRequestMethod(), body.length == 0 ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofByteArray(body));
            exchange.getRequestHeaders().forEach((name, values) ->
            {
                if (!SKIPPED_HEADERS.contains(name.toLowerCase())) {
                    values.forEach(value -> builder.header(name, value));
                }
            });
            var response = forwarder.send(builder.build(), HttpResponse.BodyHandlers.ofByteArray());
            response.headers().map().forEach((name, values) ->
            {
                if (!SKIPPED_HEADERS.contains(name.toLowerCase())) {
                    values.forEach(value -> exchange.getResponseHeaders().add(name, value));
                }
            });
            var payload = response.body();
            exchange.sendResponseHeaders(response.statusCode(), payload.length == 0 ? -1 : payload.length);
            if (payload.length > 0) {
                exchange.getResponseBody().write(payload);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            exchange.sendResponseHeaders(502, -1);
        } catch (Exception e) {
            exchange.sendResponseHeaders(502, -1);
        } finally {
            exchange.close();
        }
    }

    @Override
    public void close() {
        server.stop(0);
        forwarder.close();
    }
}
