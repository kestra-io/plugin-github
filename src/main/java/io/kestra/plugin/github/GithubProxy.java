package io.kestra.plugin.github;

import io.kestra.core.http.client.configurations.HttpConfiguration;
import io.kestra.core.http.client.configurations.ProxyConfiguration;
import io.kestra.core.runners.RunContext;
import okhttp3.Credentials;
import okhttp3.OkHttpClient;
import org.kohsuke.github.GitHubBuilder;
import org.kohsuke.github.extras.okhttp3.OkHttpGitHubConnector;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.time.Duration;

/**
 * Applies the optional `proxy` configuration to both the github-api client and Kestra's HTTP client.
 */
public final class GithubProxy {
    public static final String PROXY_PROPERTY_DESCRIPTION = """
        Optional proxy used for every request made by this component. The proxy is active when `address` is set, and `type` must then be set to `HTTP` or `SOCKS` (the default `DIRECT` means no proxy and is rejected when an address is given). \
        Set `username` and `password` for Basic proxy authentication, using secrets such as `{{ secret('PROXY_PASSWORD') }}`. SOCKS proxies do not support credentials here.""";

    private static final int PROXY_AUTH_REQUIRED = 407;

    private GithubProxy() {
    }

    private record Resolved(Proxy.Type type, String host, int port, String username, String password) {
        boolean hasCredentials() {
            return username != null && password != null;
        }
    }

    /**
     * Returns an HTTP configuration builder carrying the validated proxy, or a plain builder when no proxy address is set.
     */
    public static HttpConfiguration.HttpConfigurationBuilder httpConfiguration(RunContext runContext, ProxyConfiguration proxy) throws Exception {
        var builder = HttpConfiguration.builder();
        if (resolve(runContext, proxy) != null) {
            builder.proxy(proxy);
        }
        return builder;
    }

    /**
     * Routes the github-api client through the proxy. Without a proxy address the default connector is kept untouched.
     */
    public static void apply(GitHubBuilder builder, RunContext runContext, ProxyConfiguration proxy) throws Exception {
        var resolved = resolve(runContext, proxy);
        if (resolved == null) {
            return;
        }

        var okHttp = new OkHttpClient.Builder()
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

        okHttp.addInterceptor(chain ->
        {
            var response = chain.proceed(chain.request());
            if (response.code() == PROXY_AUTH_REQUIRED) {
                response.close();
                throw new IOException(proxyAuthMessage());
            }
            return response;
        });

        builder.withConnector(new OkHttpGitHubConnector(okHttp.build()));
    }

    /**
     * Fails with an actionable message when the proxy rejected the credentials (HTTP 407).
     */
    public static void checkStatus(int status) {
        if (status == PROXY_AUTH_REQUIRED) {
            throw new IllegalStateException(proxyAuthMessage());
        }
    }

    private static String proxyAuthMessage() {
        return "Proxy authentication failed (HTTP 407): check the `proxy.username` and `proxy.password` values.";
    }

    private static Resolved resolve(RunContext runContext, ProxyConfiguration proxy) throws Exception {
        if (proxy == null) {
            return null;
        }

        var address = runContext.render(proxy.getAddress()).as(String.class).filter(s -> !s.isBlank()).orElse(null);
        if (address == null) {
            return null;
        }

        var type = runContext.render(proxy.getType()).as(Proxy.Type.class).orElse(Proxy.Type.DIRECT);
        if (type == Proxy.Type.DIRECT) {
            throw new IllegalArgumentException("`proxy.address` is set but `proxy.type` is `DIRECT`, which means no proxy. Set `proxy.type: HTTP` (or `SOCKS`) to use the proxy.");
        }

        var port = runContext.render(proxy.getPort()).as(Integer.class)
            .orElseThrow(() -> new IllegalArgumentException("`proxy.port` is required when `proxy.address` is set."));
        var username = runContext.render(proxy.getUsername()).as(String.class).filter(s -> !s.isEmpty()).orElse(null);
        var password = runContext.render(proxy.getPassword()).as(String.class).filter(s -> !s.isEmpty()).orElse(null);

        var resolved = new Resolved(type, address, port, username, password);
        if (type == Proxy.Type.SOCKS && (username != null || password != null)) {
            throw new IllegalArgumentException("Proxy credentials are not supported with a SOCKS proxy. Remove `proxy.username` and `proxy.password`, or use `proxy.type: HTTP`.");
        }
        return resolved;
    }
}
