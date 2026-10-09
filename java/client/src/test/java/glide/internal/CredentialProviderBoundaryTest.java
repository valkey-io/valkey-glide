/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.internal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import connection_request.ConnectionRequestOuterClass.AuthenticationInfo;
import connection_request.ConnectionRequestOuterClass.ConnectionRequest;
import connection_request.ConnectionRequestOuterClass.IamCredentials;
import connection_request.ConnectionRequestOuterClass.NodeAddress;
import connection_request.ConnectionRequestOuterClass.ServiceType;
import glide.api.models.configuration.AwsCredentials;
import glide.api.models.configuration.GlideCredentialProvider;
import glide.ffi.resolvers.GlidePoolResolver;
import glide.ffi.resolvers.NativeUtils;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@Timeout(10)
public class CredentialProviderBoundaryTest {
    private static final long UNSUPPORTED_POOL_CONFIG = -3;

    @BeforeAll
    static void loadNativeLibrary() {
        NativeUtils.loadGlideLib();
    }

    private static ConnectionRequest.Builder lazyRequest() {
        return ConnectionRequest.newBuilder()
                .setLazyConnect(true)
                .addAddresses(NodeAddress.newBuilder().setHost("127.0.0.1").setPort(1));
    }

    private static byte[] lazyIamRequest(String credentialProviderKey) {
        IamCredentials iam =
                IamCredentials.newBuilder()
                        .setClusterName("test-cluster")
                        .setRegion("us-east-1")
                        .setServiceType(ServiceType.ELASTICACHE)
                        .build();
        ConnectionRequest.Builder request =
                lazyRequest()
                        .setAuthenticationInfo(
                                AuthenticationInfo.newBuilder().setUsername("test-user").setIamCredentials(iam));
        if (credentialProviderKey != null) {
            request.setCredentialProviderKey(credentialProviderKey);
        }
        return request.build().toByteArray();
    }

    @Test
    void directCreateWithProviderObjectAndNoKeyStillWorks() {
        AtomicInteger invocations = new AtomicInteger();
        GlideCredentialProvider provider =
                () -> {
                    invocations.incrementAndGet();
                    return CompletableFuture.completedFuture(
                            AwsCredentials.builder()
                                    .accessKeyId("access")
                                    .secretAccessKey("secret")
                                    .sessionToken("token")
                                    .build());
                };

        long handle = GlideNativeBridge.createClient(lazyIamRequest(null), null, provider);
        try {
            assertTrue(handle > 0, "direct provider object should create a native client");
            assertEquals(1, invocations.get());
        } finally {
            if (handle > 0) {
                GlideNativeBridge.closeClient(handle);
            }
        }
    }

    @Test
    void directCreateRejectsEveryPresentCredentialProviderKeyWithoutCreatingAHandle() {
        for (String key : new String[] {"", " ", "\t\n", "custom-provider"}) {
            assertEquals(
                    0, GlideNativeBridge.createClient(lazyIamRequest(key), null, null), "key=" + key);
        }
    }

    @Test
    void directCreateRejectsMixedProviderRepresentationsBeforeInvokingProvider() {
        AtomicInteger invocations = new AtomicInteger();
        GlideCredentialProvider provider =
                () -> {
                    invocations.incrementAndGet();
                    return CompletableFuture.completedFuture(
                            AwsCredentials.builder().accessKeyId("unused").secretAccessKey("unused").build());
                };

        for (String key : new String[] {"", " ", "custom-provider"}) {
            assertEquals(
                    0, GlideNativeBridge.createClient(lazyIamRequest(key), null, provider), "key=" + key);
        }
        assertEquals(0, invocations.get());
    }

    @Test
    void rawPoolCreateRejectsEveryPresentCredentialProviderKeyBeforeRegistration() {
        for (String key : new String[] {"", " ", "\t\n", "custom-provider"}) {
            long result = GlidePoolResolver.glidePoolCreate(1, 0, 1_000, 1_000, 0, lazyIamRequest(key));
            assertEquals(UNSUPPORTED_POOL_CONFIG, result, "key=" + key);
        }
    }
}
