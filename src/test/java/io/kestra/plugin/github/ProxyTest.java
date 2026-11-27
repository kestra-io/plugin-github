package io.kestra.plugin.github;

import io.kestra.core.http.client.configurations.ProxyConfiguration;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.common.FetchType;
import io.kestra.core.runners.RunContextFactory;
import io.kestra.plugin.github.auth.AppToken;
import io.kestra.plugin.github.issues.Comment;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

import java.io.IOException;
import java.net.InetAddress;
import java.net.Proxy;
import java.net.ServerSocket;
import java.security.KeyPairGenerator;
import java.util.Base64;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Execution(ExecutionMode.SAME_THREAD)
class ProxyTest extends AbstractGithubClientTest {
    private static final String USERNAME = "kestra";
    private static final String PASSWORD = "s3cret-proxy-password";

    @Inject
    private RunContextFactory runContextFactory;

    private ProxyConfiguration proxy(int port, String password) {
        return ProxyConfiguration.builder()
            .type(Property.ofValue(Proxy.Type.HTTP))
            .address(Property.ofValue("127.0.0.1"))
            .port(Property.ofValue(port))
            .username(Property.ofValue(USERNAME))
            .password(Property.ofValue(password))
            .build();
    }

    private Comment.CommentBuilder<?, ?> commentBuilder() {
        return Comment.builder()
            .oauthToken(Property.ofValue(""))
            .endpoint(Property.ofValue(embeddedServer.getURI().toString()))
            .repository(Property.ofValue("kestra-io/mock-kestra"))
            .issueNumber(Property.ofValue(42))
            .body(Property.ofValue("through the proxy"));
    }

    private io.kestra.plugin.github.projects.List.ListBuilder<?, ?> projectsBuilder() {
        return io.kestra.plugin.github.projects.List.builder()
            .oauthToken(Property.ofValue("test-token"))
            .endpoint(Property.ofValue(embeddedServer.getURI().toString()))
            .organization(Property.ofValue("kestra-io"))
            .projectNumber(Property.ofValue(1))
            .fetchType(Property.ofValue(FetchType.FETCH));
    }

    @Test
    void githubApiTaskGoesThroughAuthenticatedProxy() throws Exception {
        try (var proxy = new AuthenticatedProxy(USERNAME, PASSWORD)) {
            var output = commentBuilder().proxy(proxy(proxy.port(), PASSWORD)).build().run(runContextFactory.of());

            assertThat(output.getCommentId()).isEqualTo(100L);
            assertThat(proxy.rejected.get()).isGreaterThanOrEqualTo(1);
            assertThat(proxy.authenticatedUris).anyMatch(uri -> uri.endsWith("/repos/kestra-io/mock-kestra/issues/42/comments"));
        }
    }

    @Test
    void coreHttpClientTaskGoesThroughAuthenticatedProxy() throws Exception {
        try (var proxy = new AuthenticatedProxy(USERNAME, PASSWORD)) {
            var output = projectsBuilder().proxy(proxy(proxy.port(), PASSWORD)).build().run(runContextFactory.of());

            assertThat(output.getSize()).isEqualTo(1);
            assertThat(proxy.rejected.get()).isGreaterThanOrEqualTo(1);
            assertThat(proxy.authenticatedUris).anyMatch(uri -> uri.endsWith("/graphql"));
        }
    }

    @Test
    void appTokenGoesThroughAuthenticatedProxy() throws Exception {
        try (var proxy = new AuthenticatedProxy(USERNAME, PASSWORD)) {
            var output = AppToken.builder()
                .clientId(Property.ofValue("Iv23liccgbXX6rx5d4gV"))
                .installationId(Property.ofValue("52068731"))
                .privateKey(Property.ofValue(pkcs8Pem()))
                .endpoint(Property.ofValue(embeddedServer.getURI().toString()))
                .proxy(proxy(proxy.port(), PASSWORD))
                .build()
                .run(runContextFactory.of());

            assertThat(output.getToken()).isEqualTo("ghs_mocktoken_for_installation_52068731");
            assertThat(proxy.rejected.get()).isGreaterThanOrEqualTo(1);
            assertThat(proxy.authenticatedUris).anyMatch(uri -> uri.endsWith("/app/installations/52068731/access_tokens"));
        }
    }

    @Test
    void wrongCredentialsFailClearlyWithGithubApi() throws Exception {
        try (var proxy = new AuthenticatedProxy(USERNAME, PASSWORD)) {
            var task = commentBuilder().proxy(proxy(proxy.port(), "wrong-password")).build();

            assertThatThrownBy(() -> task.run(runContextFactory.of()))
                .hasStackTraceContaining("Proxy authentication failed (HTTP 407)")
                .satisfies(e -> assertThat(stack(e)).doesNotContain("wrong-password"));
            assertThat(proxy.authenticatedUris).isEmpty();
            assertThat(proxy.rejected.get()).isLessThanOrEqualTo(4);
        }
    }

    @Test
    void wrongCredentialsFailClearlyWithCoreHttpClient() throws Exception {
        try (var proxy = new AuthenticatedProxy(USERNAME, PASSWORD)) {
            var task = projectsBuilder().proxy(proxy(proxy.port(), "wrong-password")).build();

            assertThatThrownBy(() -> task.run(runContextFactory.of()))
                .hasStackTraceContaining("Proxy authentication failed (HTTP 407)")
                .satisfies(e -> assertThat(stack(e)).doesNotContain("wrong-password"));
            assertThat(proxy.authenticatedUris).isEmpty();
            assertThat(proxy.rejected.get()).isLessThanOrEqualTo(4);
        }
    }

    @Test
    void addressWithDefaultTypeFailsFast() {
        var misconfigured = ProxyConfiguration.builder().address(Property.ofValue("127.0.0.1")).port(Property.ofValue(3128)).build();

        assertThatThrownBy(() -> commentBuilder().proxy(misconfigured).build().run(runContextFactory.of()))
            .hasMessageContaining("proxy.type: HTTP");
        assertThatThrownBy(() -> projectsBuilder().proxy(misconfigured).build().run(runContextFactory.of()))
            .hasMessageContaining("proxy.type: HTTP");
    }

    @Test
    void usernameWithoutPasswordFailsFast() {
        var half = ProxyConfiguration.builder()
            .type(Property.ofValue(Proxy.Type.HTTP))
            .address(Property.ofValue("127.0.0.1"))
            .port(Property.ofValue(3128))
            .username(Property.ofValue(USERNAME))
            .build();

        assertThatThrownBy(() -> commentBuilder().proxy(half).build().run(runContextFactory.of()))
            .hasMessageContaining("must be set together");
        assertThatThrownBy(() -> projectsBuilder().proxy(half).build().run(runContextFactory.of()))
            .hasMessageContaining("must be set together");
    }

    @Test
    void socksWithCredentialsIsRejected() {
        var socks = ProxyConfiguration.builder()
            .type(Property.ofValue(Proxy.Type.SOCKS))
            .address(Property.ofValue("127.0.0.1"))
            .port(Property.ofValue(1080))
            .username(Property.ofValue(USERNAME))
            .password(Property.ofValue(PASSWORD))
            .build();

        assertThatThrownBy(() -> commentBuilder().proxy(socks).build().run(runContextFactory.of()))
            .hasMessageContaining("not supported with a SOCKS proxy")
            .satisfies(e -> assertThat(e.getMessage()).doesNotContain(PASSWORD));
    }

    @Test
    void noProxyKeepsDefaultBehaviour() throws Exception {
        var output = commentBuilder().build().run(runContextFactory.of());

        assertThat(output.getCommentId()).isEqualTo(100L);
    }

    @Test
    void socksWithoutCredentialsIsUsed() throws Exception {
        var reached = new AtomicBoolean();
        try (var socks = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            var thread = Thread.ofVirtual().start(() ->
            {
                try (var client = socks.accept()) {
                    reached.set(true);
                } catch (Exception ignored) {
                    // the test only asserts that the connection reached the SOCKS endpoint
                }
            });
            var config = ProxyConfiguration.builder()
                .type(Property.ofValue(Proxy.Type.SOCKS))
                .address(Property.ofValue("127.0.0.1"))
                .port(Property.ofValue(socks.getLocalPort()))
                .build();

            assertThatThrownBy(() -> commentBuilder().proxy(config).build().run(runContextFactory.of()));
            thread.join(5000);
            assertThat(reached).isTrue();
        }
    }

    @Test
    void connectRefusedWith407FailsClearlyOnGithubApi() throws Exception {
        try (var refusing = new ConnectRefusingProxy()) {
            var task = commentBuilder()
                .endpoint(Property.ofValue("https://api.github.test"))
                .proxy(proxy(refusing.port(), "wrong-password"))
                .build();

            assertThatThrownBy(() -> task.run(runContextFactory.of()))
                .hasStackTraceContaining("Proxy authentication failed (HTTP 407) for 127.0.0.1:" + refusing.port())
                .satisfies(e -> assertThat(stack(e)).doesNotContain("wrong-password"));
            assertThat(refusing.connects.get()).isLessThanOrEqualTo(4);
        }
    }

    @Test
    void connectRefusedWith407ReportsOnCoreHttpClient() throws Exception {
        try (var refusing = new ConnectRefusingProxy()) {
            var task = AppToken.builder()
                .clientId(Property.ofValue("Iv23liccgbXX6rx5d4gV"))
                .installationId(Property.ofValue("52068731"))
                .privateKey(Property.ofValue(pkcs8Pem()))
                .endpoint(Property.ofValue("https://api.github.test"))
                .proxy(proxy(refusing.port(), "wrong-password"))
                .build();

            assertThatThrownBy(() -> task.run(runContextFactory.of()))
                .hasStackTraceContaining("Could not open a tunnel through the proxy 127.0.0.1:" + refusing.port())
                .satisfies(e -> assertThat(stack(e)).doesNotContain("wrong-password"));
            assertThat(refusing.connects.get()).isLessThanOrEqualTo(4);
        }
    }

    private static final class ConnectRefusingProxy implements AutoCloseable {
        private final ServerSocket socket = new ServerSocket(0, 50, InetAddress.getLoopbackAddress());
        final AtomicInteger connects = new AtomicInteger();

        ConnectRefusingProxy() throws IOException {
            Thread.ofVirtual().start(() ->
            {
                while (!socket.isClosed()) {
                    try (var client = socket.accept()) {
                        var in = client.getInputStream();
                        var head = new StringBuilder();
                        int b;
                        while (!head.toString().endsWith("\r\n\r\n") && (b = in.read()) != -1) {
                            head.append((char) b);
                        }
                        if (head.toString().startsWith("CONNECT")) {
                            connects.incrementAndGet();
                        }
                        client.getOutputStream().write("HTTP/1.1 407 Proxy Authentication Required\r\nProxy-Authenticate: Basic realm=\"proxy\"\r\nContent-Length: 0\r\nConnection: close\r\n\r\n".getBytes());
                        client.getOutputStream().flush();
                    } catch (Exception ignored) {
                        // socket closed at the end of the test
                    }
                }
            });
        }

        int port() {
            return socket.getLocalPort();
        }

        @Override
        public void close() throws IOException {
            socket.close();
        }
    }

    private static String stack(Throwable throwable) {
        var builder = new StringBuilder();
        for (var t = throwable; t != null; t = t.getCause()) {
            builder.append(t).append('\n');
        }
        return builder.toString();
    }

    private static String pkcs8Pem() throws Exception {
        var generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        var encoded = Base64.getMimeEncoder(64, "\n".getBytes()).encodeToString(generator.generateKeyPair().getPrivate().getEncoded());
        return "-----BEGIN PRIVATE KEY-----\n" + encoded + "\n-----END PRIVATE KEY-----";
    }
}
