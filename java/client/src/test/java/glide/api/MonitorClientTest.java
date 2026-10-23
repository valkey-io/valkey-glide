/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.api;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import glide.api.models.configuration.AwsCredentials;
import glide.api.models.configuration.GlideClientConfiguration;
import glide.api.models.configuration.IamAuthConfig;
import glide.api.models.configuration.ServerCredentials;
import glide.api.models.configuration.ServiceType;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;

public class MonitorClientTest {

    @Test
    public void parseArgsJson_empty() {
        List<String> result = MonitorClient.parseArgsJson("[]");
        assertTrue(result.isEmpty());
    }

    @Test
    public void parseArgsJson_null() {
        List<String> result = MonitorClient.parseArgsJson(null);
        assertTrue(result.isEmpty());
    }

    @Test
    public void parseArgsJson_singleArg() {
        List<String> result = MonitorClient.parseArgsJson("[\"key\"]");
        assertEquals(Collections.singletonList("key"), result);
    }

    @Test
    public void parseArgsJson_multipleArgs() {
        List<String> result = MonitorClient.parseArgsJson("[\"key\",\"val\"]");
        assertEquals(Arrays.asList("key", "val"), result);
    }

    @Test
    public void parseArgsJson_escapedQuote() {
        // JSON: ["he said \"hi\""]
        List<String> result = MonitorClient.parseArgsJson("[\"he said \\\"hi\\\"\"]");
        assertEquals(Collections.singletonList("he said \"hi\""), result);
    }

    @Test
    public void parseArgsJson_escapedBackslash() {
        // JSON: ["a\\b"]
        List<String> result = MonitorClient.parseArgsJson("[\"a\\\\b\"]");
        assertEquals(Collections.singletonList("a\\b"), result);
    }

    @Test
    public void parseArgsJson_controlCharNewline() {
        // serde_json encodes newline as \n in JSON: ["line1\nline2"]
        List<String> result = MonitorClient.parseArgsJson("[\"line1\\nline2\"]");
        assertEquals(Collections.singletonList("line1\nline2"), result);
    }

    @Test
    void parseArgsJsonUnicodeEscape() {
        // \u0041 is 'A'
        List<String> result = MonitorClient.parseArgsJson("[\"\\u0041\"]");
        assertEquals(Collections.singletonList("A"), result);
    }

    @Test
    void monitorRejectsNullConfig() {
        assertThrows(NullPointerException.class, () -> MonitorClient.create(null));
    }

    @Test
    void monitorRejectsIamBeforeNativeInvocation() {
        GlideClientConfiguration config = iamConfig(null);

        IllegalArgumentException error =
                assertThrows(IllegalArgumentException.class, () -> MonitorClient.create(config));

        assertTrue(error.getMessage().contains("does not support IAM authentication"));
    }

    @Test
    void monitorRejectsCustomProviderIamBeforeNativeInvocation() {
        GlideClientConfiguration config =
                iamConfig(
                        () ->
                                CompletableFuture.completedFuture(
                                        AwsCredentials.builder()
                                                .accessKeyId("access")
                                                .secretAccessKey("secret")
                                                .build()));

        IllegalArgumentException error =
                assertThrows(IllegalArgumentException.class, () -> MonitorClient.create(config));

        assertTrue(error.getMessage().contains("custom IAM credentials providers"));
    }

    @Test
    void monitorAllowsPasswordAuthenticationConfiguration() {
        GlideClientConfiguration config =
                GlideClientConfiguration.builder()
                        .credentials(
                                ServerCredentials.builder()
                                        .username("monitor-user")
                                        .password("monitor-password")
                                        .build())
                        .build();

        assertDoesNotThrow(() -> MonitorClient.validateConfiguration(config));
    }

    private static GlideClientConfiguration iamConfig(
            glide.api.models.configuration.GlideCredentialProvider provider) {
        IamAuthConfig iam =
                IamAuthConfig.builder()
                        .clusterName("monitor-cluster")
                        .service(ServiceType.ELASTICACHE)
                        .region("us-east-1")
                        .credentialsProvider(provider)
                        .build();
        return GlideClientConfiguration.builder()
                .credentials(ServerCredentials.builder().username("monitor-user").iamConfig(iam).build())
                .build();
    }
}
