package io.kestra.plugin.github.repositories;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.property.Property;
import io.kestra.core.runners.RunContextFactory;
import io.kestra.core.storages.StorageInterface;
import io.kestra.core.tenant.TenantService;
import io.kestra.plugin.github.AbstractGithubClientTest;
import io.kestra.plugin.github.MockController;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@KestraTest
@Execution(ExecutionMode.SAME_THREAD)
public class DownloadArchiveTest extends AbstractGithubClientTest {
    @Inject
    private RunContextFactory runContextFactory;

    @Inject
    private StorageInterface storageInterface;

    private DownloadArchive.DownloadArchiveBuilder<?, ?> task() {
        return DownloadArchive.builder()
            .oauthToken(Property.ofValue("oauth-token"))
            .endpoint(Property.ofValue(embeddedServer.getURI().toString()))
            .repository(Property.ofValue("kestra-io/mock-kestra"));
    }

    @Test
    void tarWithSha() throws Exception {
        var sha = "a1b2c3d4e5f6789012345678901234567890abcd";
        var output = task()
            .ref(Property.ofValue(sha))
            .format(Property.ofValue(DownloadArchive.Format.TAR_GZ))
            .build()
            .run(runContextFactory.of());

        assertThat(output.getFormat()).isEqualTo("TAR_GZ");
        assertThat(output.getRef()).isEqualTo(sha);
        assertThat(output.getSize()).isEqualTo((long) MockController.TAR_BYTES.length);
        assertThat(MockController.lastPath).endsWith("/tarball/" + sha);
        assertStored(output, MockController.TAR_BYTES);
    }

    @Test
    void zipWithBranch() throws Exception {
        var output = task()
            .ref(Property.ofValue("develop"))
            .format(Property.ofValue(DownloadArchive.Format.ZIP))
            .build()
            .run(runContextFactory.of());

        assertThat(output.getFormat()).isEqualTo("ZIP");
        assertThat(output.getRef()).isEqualTo("develop");
        assertThat(output.getSize()).isEqualTo((long) MockController.ZIP_BYTES.length);
        assertThat(MockController.lastPath).endsWith("/zipball/develop");
        assertStored(output, MockController.ZIP_BYTES);
    }

    @Test
    void omittedRefUsesDefaultBranch() throws Exception {
        var output = task().build().run(runContextFactory.of());

        assertThat(output.getRef()).isEqualTo("main");
        assertThat(output.getFormat()).isEqualTo("ZIP");
        assertThat(MockController.lastPath).endsWith("/zipball/main");
        assertStored(output, MockController.ZIP_BYTES);
    }

    @Test
    void appInstallationTokenIsSent() throws Exception {
        task()
            .oauthToken(null)
            .appInstallationToken(Property.ofValue("app-installation-token"))
            .ref(Property.ofValue("main"))
            .build()
            .run(runContextFactory.of());

        // github-api sends installation tokens with the "token" scheme, same as oauthToken.
        assertThat(MockController.headers.get("authorization")).isEqualTo("token app-installation-token");
        assertThat(MockController.lastPath).endsWith("/zipball/main");
    }

    @Test
    void redirectDropsAuthorization() throws Exception {
        var followUpAuthorization = new AtomicReference<String>();
        var payloadServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        payloadServer.createContext("/archive", exchange -> {
            followUpAuthorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            exchange.getResponseHeaders().set("Content-Type", "application/octet-stream");
            exchange.sendResponseHeaders(200, MockController.ZIP_BYTES.length);
            try (var body = exchange.getResponseBody()) {
                body.write(MockController.ZIP_BYTES);
            }
        });
        payloadServer.start();
        try {
            var address = payloadServer.getAddress();
            MockController.archiveRedirect = "http://127.0.0.1:" + address.getPort() + "/archive";

            var output = task().ref(Property.ofValue("main")).build().run(runContextFactory.of());

            assertThat(MockController.headers.get("authorization")).isEqualTo("token oauth-token");
            assertThat(MockController.lastPath).endsWith("/zipball/main");
            assertThat(followUpAuthorization.get()).isNull();
            assertStored(output, MockController.ZIP_BYTES);
        } finally {
            payloadServer.stop(0);
        }
    }

    @Test
    void missingArchiveFails() {
        MockController.archiveMissing = true;

        assertThatThrownBy(() -> task().ref(Property.ofValue("missing")).build().run(runContextFactory.of()))
            .isInstanceOf(IOException.class);
    }

    private void assertStored(DownloadArchive.Output output, byte[] expected) throws IOException {
        assertThat(output.getUri()).isNotNull();
        try (var input = storageInterface.get(TenantService.MAIN_TENANT, null, output.getUri())) {
            assertThat(input.readAllBytes()).isEqualTo(expected);
        }
    }
}
