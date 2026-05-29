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

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.Signature;
import java.security.interfaces.RSAPrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
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
        var isPkcs1 = pem.contains("BEGIN RSA PRIVATE KEY");
        var stripped = pem
            .replaceAll("-----BEGIN[^-]+-----", "")
            .replaceAll("-----END[^-]+-----", "")
            .replaceAll("\\s", "");
        var decoded = Base64.getDecoder().decode(stripped);
        if (isPkcs1) {
            // The JDK's KeyFactory only reads PKCS#8 for RSA private keys. PKCS#1 keys
            // (the default GitHub gives you when you generate an App key) must be wrapped
            // in a PKCS#8 PrivateKeyInfo ASN.1 structure first. Doing the wrap manually
            // avoids pulling in BouncyCastle just for this one conversion.
            decoded = wrapPkcs1InPkcs8(decoded);
        }
        return (RSAPrivateKey) KeyFactory.getInstance("RSA")
            .generatePrivate(new PKCS8EncodedKeySpec(decoded));
    }

    /**
     * Wrap a PKCS#1 {@code RSAPrivateKey} byte sequence inside a PKCS#8 {@code PrivateKeyInfo}
     * so the JDK's {@code KeyFactory} can parse it. The emitted structure is:
     * <pre>
     * SEQUENCE {
     *   INTEGER 0                                      -- version
     *   SEQUENCE {                                     -- AlgorithmIdentifier
     *     OBJECT IDENTIFIER 1.2.840.113549.1.1.1       -- rsaEncryption
     *     NULL
     *   }
     *   OCTET STRING &lt;PKCS#1 RSAPrivateKey bytes&gt;
     * }
     * </pre>
     */
    private static byte[] wrapPkcs1InPkcs8(byte[] pkcs1) {
        var octetString = concat(new byte[]{0x04}, encodeAsn1Length(pkcs1.length), pkcs1);
        var algId = new byte[]{
            0x30, 0x0D,
            0x06, 0x09, 0x2A, (byte) 0x86, 0x48, (byte) 0x86, (byte) 0xF7, 0x0D, 0x01, 0x01, 0x01,
            0x05, 0x00
        };
        var version = new byte[]{0x02, 0x01, 0x00};
        var inner = concat(version, algId, octetString);
        return concat(new byte[]{0x30}, encodeAsn1Length(inner.length), inner);
    }

    private static byte[] encodeAsn1Length(int length) {
        if (length < 0x80) return new byte[]{(byte) length};
        if (length < 0x100) return new byte[]{(byte) 0x81, (byte) length};
        if (length < 0x10000) return new byte[]{(byte) 0x82, (byte) (length >> 8), (byte) length};
        return new byte[]{(byte) 0x83, (byte) (length >> 16), (byte) (length >> 8), (byte) length};
    }

    private static byte[] concat(byte[]... arrays) {
        int totalLength = 0;
        for (byte[] a : arrays) totalLength += a.length;
        var result = new byte[totalLength];
        int offset = 0;
        for (byte[] a : arrays) {
            System.arraycopy(a, 0, result, offset, a.length);
            offset += a.length;
        }
        return result;
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
