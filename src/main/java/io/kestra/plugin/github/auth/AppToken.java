package io.kestra.plugin.github.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.kestra.core.http.HttpRequest;
import io.kestra.core.http.client.HttpClient;
import io.kestra.core.http.client.configurations.HttpConfiguration;
import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.RunnableTask;
import io.kestra.core.models.tasks.Task;
import io.kestra.core.runners.RunContext;
import io.kestra.core.serializers.JacksonMapper;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;
import org.bouncycastle.asn1.pkcs.PrivateKeyInfo;
import org.bouncycastle.openssl.PEMKeyPair;
import org.bouncycastle.openssl.PEMParser;
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter;

import java.io.StringReader;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.Signature;
import java.security.interfaces.RSAPrivateKey;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Schema(
    title = "Issue a GitHub App installation access token",
    description = "Signs an RS256 JWT with a GitHub App's RSA private key and exchanges it at the GitHub Apps API for a 1-hour installation token. Use the returned `token` as the `appInstallationToken` input on downstream `io.kestra.plugin.github.*` tasks, or as the `Authorization: Bearer ...` value on raw HTTP requests."
)
@Plugin(
    examples = {
        @Example(
            title = "Issue an installation token and reuse it for an issue comment.",
            full = true,
            code = """
                   id: github_app_token_flow
                   namespace: company.team

                   tasks:
                     - id: token
                       type: io.kestra.plugin.github.auth.AppToken
                       clientId: "{{ secret('GITHUB_APP_CLIENT_ID') }}"
                       installationId: "52068731"
                       privateKey: "{{ secret('GITHUB_APP_PRIVATE_KEY') }}"

                     - id: comment
                       type: io.kestra.plugin.github.issues.Comment
                       appInstallationToken: "{{ outputs.token.token }}"
                       repository: kestra-io/kestra
                       issueNumber: 1347
                       body: "Triaged automatically. Execution: {{ execution.id }}"
                   """
        )
    }
)
public class AppToken extends Task implements RunnableTask<AppToken.Output> {
    private static final String DEFAULT_ENDPOINT = "https://api.github.com";
    private static final ObjectMapper MAPPER = JacksonMapper.ofJson();

    @Schema(
        title = "GitHub App client ID",
        description = "The fine-grained client identifier (`Iv23...`) or the numeric App ID. Used as the JWT `iss` claim when signing."
    )
    @NotNull
    @PluginProperty(group = "main")
    private Property<String> clientId;

    @Schema(
        title = "GitHub App installation ID",
        description = "Numeric installation identifier under the target user or organization. Find it under Settings → Integrations → Applications → Installed GitHub Apps → Configure (the number at the end of the URL)."
    )
    @NotNull
    @PluginProperty(group = "main")
    private Property<String> installationId;

    @Schema(
        title = "GitHub App private key",
        description = "PEM-encoded RSA private key for the GitHub App. Both PKCS#1 (`-----BEGIN RSA PRIVATE KEY-----`, the default GitHub emits when you generate a key) and PKCS#8 (`-----BEGIN PRIVATE KEY-----`) formats are accepted."
    )
    @NotNull
    @PluginProperty(group = "connection", secret = true)
    private Property<String> privateKey;

    @Schema(
        title = "GitHub API endpoint",
        description = "GitHub or GitHub Enterprise API base URL such as `https://api.github.com` or `https://ghe.acme.com/api/v3`. Defaults to `https://api.github.com` when unset."
    )
    @Builder.Default
    @PluginProperty(group = "connection")
    private Property<String> endpoint = Property.ofValue(DEFAULT_ENDPOINT);

    @Override
    public AppToken.Output run(RunContext runContext) throws Exception {
        var logger = runContext.logger();

        var rClientId = runContext.render(this.clientId).as(String.class).orElseThrow();
        var rInstallationId = runContext.render(this.installationId).as(String.class).orElseThrow();
        var rPrivateKey = runContext.render(this.privateKey).as(String.class).orElseThrow();
        var rEndpoint = runContext.render(this.endpoint).as(String.class).orElse(DEFAULT_ENDPOINT);

        var jwt = signJwt(rClientId, rPrivateKey);
        logger.debug("Signed JWT for GitHub App client {}", rClientId);

        var req = HttpRequest.builder()
            .uri(URI.create(rEndpoint + "/app/installations/" + rInstallationId + "/access_tokens"))
            .method("POST")
            .addHeader("Authorization", "Bearer " + jwt)
            .addHeader("Accept", "application/vnd.github+json")
            .addHeader("X-GitHub-Api-Version", "2022-11-28")
            .build();

        try (var http = new HttpClient(runContext, HttpConfiguration.builder().build())) {
            var resp = http.request(req, String.class);

            var status = resp.getStatus().getCode();
            if (status < 200 || status >= 300) {
                logger.debug("GitHub installation token exchange response body: {}", resp.getBody());
                throw new IllegalStateException(
                    "GitHub installation token exchange failed with HTTP " + status
                );
            }

            var body = MAPPER.readTree(resp.getBody());
            if (body.get("token") == null) {
                throw new IllegalStateException(
                    "GitHub response did not contain a `token` field."
                );
            }

            return Output.builder()
                .token(body.get("token").asText())
                .expiresAt(body.has("expires_at") ? Instant.parse(body.get("expires_at").asText()) : null)
                .build();
        }
    }

    private static String signJwt(String clientId, String pem) throws Exception {
        // iat is set 60 seconds in the past to tolerate clock skew between the worker and GitHub's
        // auth servers. exp is set 530 seconds in the future, keeping the total signed window under
        // GitHub's 600-second cap with a 10-second buffer for boundary-case rejection.
        var now = System.currentTimeMillis() / 1000L;
        var headerJson = "{\"alg\":\"RS256\",\"typ\":\"JWT\"}";
        var payloadJson = MAPPER.writeValueAsString(Map.of(
            "iat", now - 60,
            "exp", now + 530,
            "iss", clientId
        ));

        var header = base64UrlEncode(headerJson.getBytes(StandardCharsets.UTF_8));
        var payload = base64UrlEncode(payloadJson.getBytes(StandardCharsets.UTF_8));
        var signingInput = header + "." + payload;

        var signer = Signature.getInstance("SHA256withRSA");
        signer.initSign(parsePrivateKey(pem));
        signer.update(signingInput.getBytes(StandardCharsets.UTF_8));
        return signingInput + "." + base64UrlEncode(signer.sign());
    }

    private static RSAPrivateKey parsePrivateKey(String pem) throws Exception {
        // BouncyCastle's PEMParser reads both PKCS#1 (`-----BEGIN RSA PRIVATE KEY-----`,
        // the default GitHub emits) and PKCS#8 (`-----BEGIN PRIVATE KEY-----`) transparently.
        try (var reader = new PEMParser(new StringReader(pem))) {
            var obj = reader.readObject();
            PrivateKeyInfo keyInfo = switch (obj) {
                case PEMKeyPair pair -> pair.getPrivateKeyInfo();
                case PrivateKeyInfo info -> info;
                case null -> throw new IllegalArgumentException("Empty or unreadable PEM input.");
                default -> throw new IllegalArgumentException("Unsupported PEM object: " + obj.getClass().getName());
            };
            return (RSAPrivateKey) new JcaPEMKeyConverter().getPrivateKey(keyInfo);
        }
    }

    private static String base64UrlEncode(byte[] data) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(data);
    }

    @Builder
    @Getter
    public static class Output implements io.kestra.core.models.tasks.Output {
        @Schema(
            title = "Installation access token",
            description = "Bearer token valid for one hour from issuance. Pass as `appInstallationToken` to downstream `io.kestra.plugin.github.*` tasks, or as `Authorization: Bearer ...` for raw HTTP requests."
        )
        private final String token;

        @Schema(
            title = "Expiration timestamp",
            description = "Instant at which GitHub will reject the token, parsed from the API response."
        )
        private final Instant expiresAt;
    }
}
