package io.kestra.plugin.github.auth;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.property.Property;
import io.kestra.core.runners.RunContextFactory;
import io.kestra.plugin.github.AbstractGithubClientTest;
import io.kestra.plugin.github.MockController;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;

@KestraTest
public class AppTokenTest extends AbstractGithubClientTest {
    @Inject
    private RunContextFactory runContextFactory;

    @Test
    void issuesInstallationTokenWithPkcs8Key() throws Exception {
        // Generate a throwaway RSA-2048 key pair so the test stays hermetic, no key files
        // committed, no shared fixtures, fresh material every run.
        KeyPair keyPair = generateRsaKeyPair();
        String privateKeyPem = toPkcs8Pem((RSAPrivateKey) keyPair.getPrivate());

        runAndAssert(privateKeyPem);
    }

    @Test
    void issuesInstallationTokenWithPkcs1Key() throws Exception {
        // PKCS#1 is the format GitHub emits when an App's private key is generated from the UI.
        // Re-encode the same key material as PKCS#1 to cover the wrapPkcs1InPkcs8 ASN.1 path
        // that PKCS#8 input skips entirely.
        KeyPair keyPair = generateRsaKeyPair();
        String privateKeyPem = toPkcs1Pem((RSAPrivateKey) keyPair.getPrivate());

        runAndAssert(privateKeyPem);
    }

    private void runAndAssert(String privateKeyPem) throws Exception {
        var task = AppToken.builder()
            .clientId(Property.ofValue("Iv23liccgbXX6rx5d4gV"))
            .installationId(Property.ofValue("52068731"))
            .privateKey(Property.ofValue(privateKeyPem))
            .endpoint(Property.ofValue(embeddedServer.getURI().toString()))
            .build();

        AppToken.Output output = task.run(runContextFactory.of());

        // 1. The mock returns a deterministic token shape tied to the installation id,
        //    verifying it ensures the JSON response was parsed correctly.
        assertThat(output.getToken()).isEqualTo("ghs_mocktoken_for_installation_52068731");
        assertThat(output.getExpiresAt()).isEqualTo(Instant.parse("2099-12-31T23:59:59Z"));

        // 2. The Authorization header captured by the mock should carry a well-formed JWT.
        //    The signing exercises the full PEM -> RSAPrivateKey -> SHA256withRSA path; if any
        //    step were broken, we'd never get a `Bearer xxx.yyy.zzz`-shaped value here.
        String authHeader = MockController.headers.get("authorization");
        assertThat(authHeader).startsWith("Bearer ");
        String jwt = authHeader.substring("Bearer ".length());
        assertThat(jwt.split("\\.")).hasSize(3);

        // 3. The JWT payload must carry the right `iss` claim (matches the App's client id).
        //    Decoding base64url without depending on a JWT library keeps the test JDK-only.
        String payloadSegment = jwt.split("\\.")[1];
        String payloadJson = new String(Base64.getUrlDecoder().decode(payloadSegment), StandardCharsets.UTF_8);
        assertThat(payloadJson).contains("\"iss\":\"Iv23liccgbXX6rx5d4gV\"");
    }

    private static KeyPair generateRsaKeyPair() throws Exception {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
        kpg.initialize(2048);
        return kpg.generateKeyPair();
    }

    /**
     * Render an RSA private key in PKCS#8 PEM form, the format the JDK's KeyFactory emits
     * natively from {@code key.getEncoded()}.
     */
    private static String toPkcs8Pem(RSAPrivateKey key) {
        String base64 = Base64.getMimeEncoder(64, "\n".getBytes()).encodeToString(key.getEncoded());
        return "-----BEGIN PRIVATE KEY-----\n" + base64 + "\n-----END PRIVATE KEY-----\n";
    }

    /**
     * Render an RSA private key in PKCS#1 PEM form by stripping the fixed PKCS#8 wrapper
     * from the JDK-emitted encoding. For a 2048-bit RSA key with rsaEncryption AlgorithmIdentifier
     * the wrapper is always the same 26 bytes (SEQUENCE header, version, algId, OCTET STRING
     * header), so the inner PKCS#1 body starts at a known offset.
     */
    private static String toPkcs1Pem(RSAPrivateKey key) {
        byte[] pkcs8 = key.getEncoded();
        byte[] pkcs1 = Arrays.copyOfRange(pkcs8, 26, pkcs8.length);
        String base64 = Base64.getMimeEncoder(64, "\n".getBytes()).encodeToString(pkcs1);
        return "-----BEGIN RSA PRIVATE KEY-----\n" + base64 + "\n-----END RSA PRIVATE KEY-----\n";
    }
}
