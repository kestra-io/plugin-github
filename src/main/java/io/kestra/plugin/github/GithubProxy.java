package io.kestra.plugin.github;

import io.kestra.core.exceptions.IllegalVariableEvaluationException;
import io.kestra.core.http.HttpRequest;
import io.kestra.core.http.HttpResponse;
import io.kestra.core.http.client.HttpClient;
import io.kestra.core.http.client.HttpClientException;
import io.kestra.core.http.client.configurations.HttpConfiguration;
import io.kestra.core.http.client.configurations.ProxyConfiguration;
import io.kestra.core.runners.RunContext;
import okhttp3.ConnectionPool;
import okhttp3.Credentials;
import okhttp3.OkHttpClient;
import okhttp3.Response;
import org.kohsuke.github.GitHubBuilder;
import org.kohsuke.github.extras.okhttp3.OkHttpGitHubConnector;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.time.Duration;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

public final class GithubProxy {
    public static final String PROXY_PROPERTY_DESCRIPTION = """
        Optional proxy used for every request made by this component. The proxy is active when `address` is set, and `type` must then be set to `HTTP` or `SOCKS` (the default `DIRECT` means no proxy and is rejected when an address is given). \
        Set `username` and `password` for Basic proxy authentication, using secrets such as `{{ secret('PROXY_PASSWORD') }}`. SOCKS proxies do not support credentials here.""";

    private static final int PROXY_AUTH_REQUIRED = 407;
    private static final String PROXY_TUNNEL_AUTH_FAILURE = "Failed to authenticate with proxy";

    private GithubProxy() {
    }

    private record Resolved(Proxy.Type type, String host, int port, String username, String password) {
        boolean hasCredentials() {
            return username != null && password != null;
        }

        @Override
        public String toString() {
            return "Resolved[type=%s, host=%s, port=%d]".formatted(type, host, port);
        }
    }

    public static HttpConfiguration.HttpConfigurationBuilder httpConfiguration(RunContext runContext, ProxyConfiguration proxy) throws Exception {
        var builder = HttpConfiguration.builder();
        if (resolve(runContext, proxy) != null) {
            builder.proxy(proxy);
        }
        return builder;
    }

    public static void apply(GitHubBuilder builder, RunContext runContext, ProxyConfiguration proxy) throws Exception {
        var resolved = resolve(runContext, proxy);
        if (resolved == null) {
            return;
        }

        // A client is built per run or poll and never closed: a short keep-alive lets idle proxy connections expire instead of lingering for OkHttp's 5 minute default
        var okHttp = new OkHttpClient.Builder()
            .connectionPool(new ConnectionPool(2, 5, TimeUnit.SECONDS))
            .proxy(new Proxy(resolved.type(), new InetSocketAddress(resolved.host(), resolved.port())))
            .connectTimeout(Duration.ofSeconds(10))
            .readTimeout(Duration.ZERO)
            .writeTimeout(Duration.ZERO);

        if (resolved.hasCredentials()) {
            var credentials = Credentials.basic(resolved.username(), resolved.password());
            okHttp.proxyAuthenticator((route, response) -> response.request().header("Proxy-Authorization") != null
                ? null
                : response.request().newBuilder().header("Proxy-Authorization", credentials).build());
        }

        var message = proxyAuthMessage(resolved.host(), resolved.port());
        okHttp.addInterceptor(chain ->
        {
            Response response;
            try {
                response = chain.proceed(chain.request());
            } catch (IOException e) {
                // OkHttp throws a plain IOException when the CONNECT tunnel to an HTTPS target is refused with 407
                if (PROXY_TUNNEL_AUTH_FAILURE.equals(e.getMessage())) {
                    throw new IOException(message, e);
                }
                throw e;
            }
            if (response.code() == PROXY_AUTH_REQUIRED) {
                response.close();
                throw new IOException(message);
            }
            return response;
        });

        builder.withConnector(new OkHttpGitHubConnector(okHttp.build()));
    }

    public static void checkStatus(RunContext runContext, ProxyConfiguration proxy, int status) throws Exception {
        if (status == PROXY_AUTH_REQUIRED) {
            var resolved = resolve(runContext, proxy);
            throw new IllegalStateException(resolved == null
                ? "HTTP 407 Proxy Authentication Required received."
                : proxyAuthMessage(resolved.host(), resolved.port()));
        }
    }

    /**
     * Kestra's HTTP client surfaces a refused CONNECT tunnel (HTTP 407 over HTTPS) as an opaque NullPointerException from
     * Apache HttpClient; this subclass translates it into an actionable message without touching core.
     */
    public static class Client extends HttpClient {
        private final String proxyTarget;

        public Client(RunContext runContext, ProxyConfiguration proxy, HttpConfiguration configuration) throws Exception {
            super(runContext, configuration);
            var resolved = resolve(runContext, proxy);
            this.proxyTarget = resolved == null ? null : resolved.host() + ":" + resolved.port();
        }

        @Override
        public <T> HttpResponse<T> request(HttpRequest request, Class<T> cls) throws HttpClientException, IllegalVariableEvaluationException {
            try {
                return super.request(request, cls);
            } catch (NullPointerException e) {
                throw tunnelFailure(e);
            }
        }

        @Override
        public HttpResponse<Void> request(HttpRequest request, Consumer<HttpResponse<InputStream>> consumer) throws HttpClientException, IllegalVariableEvaluationException {
            try {
                return super.request(request, consumer);
            } catch (NullPointerException e) {
                throw tunnelFailure(e);
            }
        }

        private RuntimeException tunnelFailure(NullPointerException e) {
            // Core builds an error response from the CONNECT failure, whose request is null: HttpService.safeURI called from HttpResponseFailure
            var frames = e.getStackTrace();
            var fromRefusedTunnel = frames.length > 0
                && "io.kestra.core.http.HttpService".equals(frames[0].getClassName())
                && "safeURI".equals(frames[0].getMethodName())
                && Arrays.stream(frames).anyMatch(frame -> frame.getClassName().contains("HttpResponseFailure"));
            if (proxyTarget == null || !fromRefusedTunnel) {
                return e;
            }
            return new IllegalStateException("Could not open a tunnel through the proxy %s, most likely because it rejected the credentials (HTTP 407): check the `proxy.username` and `proxy.password` values.".formatted(proxyTarget), e);
        }
    }

    private static String proxyAuthMessage(String host, int port) {
        return "Proxy authentication failed (HTTP 407) for %s:%d: check the `proxy.username` and `proxy.password` values.".formatted(host, port);
    }

    private static Resolved resolve(RunContext runContext, ProxyConfiguration proxy) throws Exception {
        if (proxy == null) {
            return null;
        }

        var rAddress = runContext.render(proxy.getAddress()).as(String.class).filter(s -> !s.isBlank()).orElse(null);
        if (rAddress == null) {
            return null;
        }

        var rType = runContext.render(proxy.getType()).as(Proxy.Type.class).orElse(Proxy.Type.DIRECT);
        if (rType == Proxy.Type.DIRECT) {
            throw new IllegalArgumentException("`proxy.address` is set but `proxy.type` is `DIRECT`, which means no proxy. Set `proxy.type: HTTP` (or `SOCKS`) to use the proxy.");
        }

        var rPort = runContext.render(proxy.getPort()).as(Integer.class)
            .orElseThrow(() -> new IllegalArgumentException("`proxy.port` is required when `proxy.address` is set."));
        var rUsername = runContext.render(proxy.getUsername()).as(String.class).filter(s -> !s.isEmpty()).orElse(null);
        var rPassword = runContext.render(proxy.getPassword()).as(String.class).filter(s -> !s.isEmpty()).orElse(null);

        if ((rUsername == null) != (rPassword == null)) {
            throw new IllegalArgumentException("`proxy.username` and `proxy.password` must be set together: set both for Basic proxy authentication, or neither.");
        }
        if (rType == Proxy.Type.SOCKS && rUsername != null) {
            throw new IllegalArgumentException("Proxy credentials are not supported with a SOCKS proxy. Remove `proxy.username` and `proxy.password`, or use `proxy.type: HTTP`.");
        }
        return new Resolved(rType, rAddress, rPort, rUsername, rPassword);
    }
}
