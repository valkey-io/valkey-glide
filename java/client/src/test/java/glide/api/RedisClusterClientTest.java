/** Copyright GLIDE-for-Redis Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.api;

import static glide.api.models.configuration.RequestRoutingConfiguration.SimpleRoute.ALL_NODES;
import static glide.api.models.configuration.RequestRoutingConfiguration.SimpleRoute.RANDOM;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static redis_request.RedisRequestOuterClass.RequestType.CustomCommand;
import static redis_request.RedisRequestOuterClass.RequestType.Info;

import glide.api.models.ClusterValue;
import glide.api.models.commands.InfoOptions;
import glide.api.models.configuration.RequestRoutingConfiguration;
import glide.managers.CommandManager;
import glide.managers.ConnectionManager;
import glide.managers.RedisExceptionCheckedFunction;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import lombok.SneakyThrows;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import redis_request.RedisRequestOuterClass;
import response.ResponseOuterClass.Response;

public class RedisClusterClientTest {

    RedisClusterClient service;

    ConnectionManager connectionManager;

    CommandManager commandManager;

    @BeforeEach
    public void setUp() {
        connectionManager = mock(ConnectionManager.class);
        commandManager = mock(CommandManager.class);
        service = new RedisClusterClient(connectionManager, commandManager);
    }

    @Test
    @SneakyThrows
    public void customCommand_returns_single_value() {
        var commandManager = new TestCommandManager(null);

        var client = new TestClient(commandManager, "TEST");

        var value = client.customCommand().get();
        assertAll(
                () -> assertTrue(value.hasSingleData()),
                () -> assertEquals("TEST", value.getSingleValue()));
    }

    @Test
    @SneakyThrows
    public void customCommand_returns_multi_value() {
        var commandManager = new TestCommandManager(null);

        var data = Map.of("key1", "value1", "key2", "value2");
        var client = new TestClient(commandManager, data);

        var value = client.customCommand().get();
        assertAll(
                () -> assertTrue(value.hasMultiData()), () -> assertEquals(data, value.getMultiValue()));
    }

    @Test
    @SneakyThrows
    // test checks that even a map returned as a single value when single node route is used
    public void customCommand_with_single_node_route_returns_single_value() {
        var commandManager = new TestCommandManager(null);

        var data = Map.of("key1", "value1", "key2", "value2");
        var client = new TestClient(commandManager, data);

        var value = client.customCommand(RANDOM).get();
        assertAll(
                () -> assertTrue(value.hasSingleData()), () -> assertEquals(data, value.getSingleValue()));
    }

    @Test
    @SneakyThrows
    public void customCommand_with_multi_node_route_returns_multi_value() {
        var commandManager = new TestCommandManager(null);

        var data = Map.of("key1", "value1", "key2", "value2");
        var client = new TestClient(commandManager, data);

        var value = client.customCommand(ALL_NODES).get();
        assertAll(
                () -> assertTrue(value.hasMultiData()), () -> assertEquals(data, value.getMultiValue()));
    }

    private static class TestClient extends RedisClusterClient {

        private final Object object;

        public TestClient(CommandManager commandManager, Object objectToReturn) {
            super(null, commandManager);
            object = objectToReturn;
        }

        @Override
        protected Object handleObjectResponse(Response response) {
            return object;
        }
    }

    private static class TestCommandManager extends CommandManager {

        private final Response response;

        public TestCommandManager(Response responseToReturn) {
            super(null);
            response = responseToReturn;
        }

        @Override
        public <T> CompletableFuture<T> submitNewCommand(
                RedisRequestOuterClass.RedisRequest.Builder command,
                RedisExceptionCheckedFunction<Response, T> responseHandler) {
            return CompletableFuture.supplyAsync(() -> responseHandler.apply(response));
        }
    }

    @SneakyThrows
    @Test
    public void customCommand_success() {
        // setup
        String key = "testKey";
        String value = "testValue";
        String cmd = "GETSTRING";
        String[] arguments = new String[] {cmd, key};
        CompletableFuture<ClusterValue<Object>> testResponse = mock(CompletableFuture.class);
        when(testResponse.get()).thenReturn(ClusterValue.of(value));
        when(commandManager.<ClusterValue<Object>>submitNewCommand(
                        eq(CustomCommand), any(), any(), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<ClusterValue<Object>> response = service.customCommand(arguments);

        // verify
        assertEquals(testResponse, response);
        ClusterValue clusterValue = response.get();
        assertTrue(clusterValue.hasSingleData());
        String payload = (String) clusterValue.getSingleValue();
        assertEquals(value, payload);
    }

    @SneakyThrows
    @Test
    public void customCommand_interruptedException() {
        // setup
        String key = "testKey";
        String cmd = "GETSTRING";
        String[] arguments = new String[] {cmd, key};
        CompletableFuture<ClusterValue<Object>> testResponse = mock(CompletableFuture.class);
        InterruptedException interruptedException = new InterruptedException();
        when(testResponse.get()).thenThrow(interruptedException);
        when(commandManager.<ClusterValue<Object>>submitNewCommand(
                        eq(CustomCommand), any(), any(), any()))
                .thenReturn(testResponse);

        // exercise
        InterruptedException exception =
                assertThrows(
                        InterruptedException.class,
                        () -> {
                            CompletableFuture<ClusterValue<Object>> response = service.customCommand(arguments);
                            response.get();
                        });

        // verify
        assertEquals(interruptedException, exception);
    }

    @SneakyThrows
    @Test
    public void info_returns_string() {
        // setup
        CompletableFuture<ClusterValue<Map>> testResponse = mock(CompletableFuture.class);
        Map testPayload = new HashMap<String, String>();
        testPayload.put("addr1", "value1");
        testPayload.put("addr1", "value2");
        testPayload.put("addr1", "value3");
        when(testResponse.get()).thenReturn(ClusterValue.of(testPayload));
        when(commandManager.<ClusterValue<Map>>submitNewCommand(
                        eq(Info), eq(new String[0]), any(), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<ClusterValue<String>> response = service.info();

        // verify
        assertEquals(testResponse, response);
        ClusterValue<String> clusterValue = response.get();
        assertTrue(clusterValue.hasMultiData());
        Map<String, String> payload = clusterValue.getMultiValue();
        assertEquals(testPayload, payload);
    }

    @SneakyThrows
    @Test
    public void info_with_route_returns_string() {
        // setup
        CompletableFuture<ClusterValue<Map>> testResponse = mock(CompletableFuture.class);
        Map<String, String> testClusterValue = Map.of("addr1", "addr1 result", "addr2", "addr2 result");
        RequestRoutingConfiguration.Route route = RequestRoutingConfiguration.SimpleRoute.ALL_NODES;
        when(testResponse.get()).thenReturn(ClusterValue.of(testClusterValue));
        when(commandManager.<ClusterValue<Map>>submitNewCommand(eq(Info), any(), any(), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<ClusterValue<String>> response = service.info(route);

        // verify
        assertEquals(testResponse, response);
        ClusterValue<String> clusterValue = response.get();
        assertTrue(clusterValue.hasMultiData());
        Map<String, String> clusterMap = clusterValue.getMultiValue();
        assertEquals("addr1 result", clusterMap.get("addr1"));
        assertEquals("addr2 result", clusterMap.get("addr2"));
    }

    @SneakyThrows
    @Test
    public void info_with_route_with_infoOptions_returns_string() {
        // setup
        String[] infoArguments = new String[] {"ALL", "DEFAULT"};
        CompletableFuture<ClusterValue<Map>> testResponse = mock(CompletableFuture.class);
        Map<String, String> testClusterValue = Map.of("addr1", "addr1 result", "addr2", "addr2 result");
        when(testResponse.get()).thenReturn(ClusterValue.of(testClusterValue));
        RequestRoutingConfiguration.Route route = RequestRoutingConfiguration.SimpleRoute.ALL_PRIMARIES;
        when(commandManager.<ClusterValue<Map>>submitNewCommand(
                        eq(Info), eq(infoArguments), any(), any()))
                .thenReturn(testResponse);

        // exercise
        InfoOptions options =
                InfoOptions.builder()
                        .section(InfoOptions.Section.ALL)
                        .section(InfoOptions.Section.DEFAULT)
                        .build();
        CompletableFuture<ClusterValue<String>> response = service.info(options, route);

        // verify
        assertEquals(testResponse, response);
        ClusterValue<String> clusterValue = response.get();
        assertTrue(clusterValue.hasMultiData());
        Map<String, String> clusterMap = clusterValue.getMultiValue();
        assertEquals("addr1 result", clusterMap.get("addr1"));
        assertEquals("addr2 result", clusterMap.get("addr2"));
    }
}
