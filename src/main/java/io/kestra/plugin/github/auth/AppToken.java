package io.kestra.plugin.github.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.RunnableTask;
import io.kestra.core.models.tasks.Task;
import io.kestra.core.runners.RunContext;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import lombok.experimental.SuperBuilder;
import org.slf4j.Logger;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.Signature;
import java.security.interfaces.RSAPrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;

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
    @PluginProperty(group = "connection")
    private Property<String> endpoint;

    @Override
    public AppToken.Output run(RunContext runContext) throws Exception {
        Logger logger = runContext.logger();

        String clientIdR = runContext.render(this.clientId).as(String.class).orElseThrow();
        String installationIdR = runContext.render(this.installationId).as(String.class).orElseThrow();
        String privateKeyR = runContext.render(this.privateKey).as(String.class).orElseThrow();
        String endpointR = runContext.render(this.endpoint).as(String.class).orElse("https://api.github.com");

        String jwt = signJwt(clientIdR, privateKeyR);
        logger.debug("Signed JWT for GitHub App client {}", clientIdR);

        HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

        HttpRequest req = HttpRequest.newBuilder()
            .uri(URI.create(endpointR + "/app/installations/" + installationIdR + "/access_tokens"))
            .timeout(Duration.ofSeconds(30))
            .header("Authorization", "Bearer " + jwt)
            .header("Accept", "application/vnd.github+json")
            .header("X-GitHub-Api-Version", "2022-11-28")
            .header("User-Agent", "kestra-plugin-github")
            .POST(HttpRequest.BodyPublishers.noBody())
            .build();

        HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());

        if (resp.statusCode() < 200 || resp.statusCode() >= 300) {
            throw new IllegalStateException(
                "GitHub installation token exchange failed with HTTP " + resp.statusCode() + ": " + resp.body()
            );
        }

        JsonNode body = new ObjectMapper().readTree(resp.body());
        if (body.get("token") == null) {
            throw new IllegalStateException(
                "GitHub response did not contain a `token` field. Body: " + resp.body()
            );
        }

        return Output.builder()
            .token(body.get("token").asText())
            .expiresAt(body.has("expires_at") ? Instant.parse(body.get("expires_at").asText()) : null)
            .build();
    }

    // -------- JWT signing --------

    private static String signJwt(String clientId, String pem) throws Exception {
        // iat is set 60 seconds in the past to tolerate small clock skew between the worker
        // and GitHub's auth servers. The total JWT lifetime stays under GitHub's 10-minute maximum.
        long now = System.currentTimeMillis() / 1000L;
        String headerJson = "{\"alg\":\"RS256\",\"typ\":\"JWT\"}";
        String payloadJson = String.format(
            "{\"iat\":%d,\"exp\":%d,\"iss\":\"%s\"}",
            now - 60, now + 540, clientId
        );

        String header = base64UrlEncode(headerJson.getBytes(StandardCharsets.UTF_8));
        String payload = base64UrlEncode(payloadJson.getBytes(StandardCharsets.UTF_8));
        String signingInput = header + "." + payload;

        Signature signer = Signature.getInstance("SHA256withRSA");
        signer.initSign(parsePrivateKey(pem));
        signer.update(signingInput.getBytes(StandardCharsets.UTF_8));
        return signingInput + "." + base64UrlEncode(signer.sign());
    }

    private static RSAPrivateKey parsePrivateKey(String pem) throws Exception {
        boolean isPkcs1 = pem.contains("BEGIN RSA PRIVATE KEY");
        String stripped = pem
            .replaceAll("-----BEGIN[^-]+-----", "")
            .replaceAll("-----END[^-]+-----", "")
            .replaceAll("\\s", "");
        byte[] decoded = Base64.getDecoder().decode(stripped);
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
        byte[] octetString = concat(new byte[]{0x04}, encodeAsn1Length(pkcs1.length), pkcs1);
        byte[] algId = new byte[]{
            0x30, 0x0D,
            0x06, 0x09, 0x2A, (byte) 0x86, 0x48, (byte) 0x86, (byte) 0xF7, 0x0D, 0x01, 0x01, 0x01,
            0x05, 0x00
        };
        byte[] version = new byte[]{0x02, 0x01, 0x00};
        byte[] inner = concat(version, algId, octetString);
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
        byte[] result = new byte[totalLength];
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
