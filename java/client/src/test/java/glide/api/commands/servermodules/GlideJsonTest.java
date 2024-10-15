/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.api.commands.servermodules;

import static glide.api.models.GlideString.gs;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import glide.api.GlideClient;
import glide.api.models.GlideString;
import glide.api.models.commands.ConditionalChange;
import glide.api.models.commands.json.JsonGetOptions;
import glide.utils.ArgsBuilder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.concurrent.CompletableFuture;
import lombok.SneakyThrows;
import org.apache.commons.lang3.ArrayUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class GlideJsonTest {

    private GlideClient glideClient;

    @BeforeEach
    void setUp() {
        glideClient = mock(GlideClient.class, RETURNS_DEEP_STUBS);
    }

    @Test
    @SneakyThrows
    void set_returns_success() {
        // setup
        String key = "testKey";
        String path = "$";
        String jsonValue = "{\"a\": 1.0, \"b\": 2}";
        CompletableFuture<String> expectedResponse = new CompletableFuture<>();
        String expectedResponseValue = "OK";
        expectedResponse.complete(expectedResponseValue);
        when(glideClient
                        .customCommand(eq(new String[] {GlideJson.JSON_SET, key, path, jsonValue}))
                        .<String>thenApply(any()))
                .thenReturn(expectedResponse);

        // exercise
        CompletableFuture<String> actualResponse = GlideJson.set(glideClient, key, path, jsonValue);
        String actualResponseValue = actualResponse.get();

        // verify
        assertEquals(expectedResponse, actualResponse);
        assertEquals(expectedResponseValue, actualResponseValue);
    }

    @Test
    @SneakyThrows
    void set_binary_returns_success() {
        // setup
        GlideString key = gs("testKey");
        GlideString path = gs("$");
        GlideString jsonValue = gs("{\"a\": 1.0, \"b\": 2}");
        CompletableFuture<String> expectedResponse = new CompletableFuture<>();
        String expectedResponseValue = "OK";
        expectedResponse.complete(expectedResponseValue);
        when(glideClient
                        .customCommand(eq(new GlideString[] {gs(GlideJson.JSON_SET), key, path, jsonValue}))
                        .<String>thenApply(any()))
                .thenReturn(expectedResponse);

        // exercise
        CompletableFuture<String> actualResponse = GlideJson.set(glideClient, key, path, jsonValue);
        String actualResponseValue = actualResponse.get();

        // verify
        assertEquals(expectedResponse, actualResponse);
        assertEquals(expectedResponseValue, actualResponseValue);
    }

    @Test
    @SneakyThrows
    void set_with_condition_returns_success() {
        // setup
        String key = "testKey";
        String path = "$";
        String jsonValue = "{\"a\": 1.0, \"b\": 2}";
        ConditionalChange setCondition = ConditionalChange.ONLY_IF_DOES_NOT_EXIST;
        CompletableFuture<String> expectedResponse = new CompletableFuture<>();
        String expectedResponseValue = "OK";
        expectedResponse.complete(expectedResponseValue);
        when(glideClient
                        .customCommand(
                                eq(
                                        new String[] {
                                            GlideJson.JSON_SET, key, path, jsonValue, setCondition.getValkeyApi()
                                        }))
                        .<String>thenApply(any()))
                .thenReturn(expectedResponse);

        // exercise
        CompletableFuture<String> actualResponse =
                GlideJson.set(glideClient, key, path, jsonValue, setCondition);
        String actualResponseValue = actualResponse.get();

        // verify
        assertEquals(expectedResponse, actualResponse);
        assertEquals(expectedResponseValue, actualResponseValue);
    }

    @Test
    @SneakyThrows
    void set_binary_with_condition_returns_success() {
        // setup
        GlideString key = gs("testKey");
        GlideString path = gs("$");
        GlideString jsonValue = gs("{\"a\": 1.0, \"b\": 2}");
        ConditionalChange setCondition = ConditionalChange.ONLY_IF_DOES_NOT_EXIST;
        CompletableFuture<String> expectedResponse = new CompletableFuture<>();
        String expectedResponseValue = "OK";
        expectedResponse.complete(expectedResponseValue);
        when(glideClient
                        .customCommand(
                                eq(
                                        new GlideString[] {
                                            gs(GlideJson.JSON_SET), key, path, jsonValue, gs(setCondition.getValkeyApi())
                                        }))
                        .<String>thenApply(any()))
                .thenReturn(expectedResponse);

        // exercise
        CompletableFuture<String> actualResponse =
                GlideJson.set(glideClient, key, path, jsonValue, setCondition);
        String actualResponseValue = actualResponse.get();

        // verify
        assertEquals(expectedResponse, actualResponse);
        assertEquals(expectedResponseValue, actualResponseValue);
    }

    @Test
    @SneakyThrows
    void get_with_single_path_returns_success() {
        // setup
        String key = "testKey";
        String path = "$";
        CompletableFuture<Object> expectedResponse = new CompletableFuture<>();
        String expectedResponseValue = "{\"a\": 1.0, \"b\": 2}";
        expectedResponse.complete(expectedResponseValue);
        when(glideClient.customCommand(eq(new String[] {GlideJson.JSON_GET, key, path})))
                .thenReturn(expectedResponse);

        // exercise
        CompletableFuture<Object> actualResponse = GlideJson.get(glideClient, key, path);
        String actualResponseValue = (String) actualResponse.get();

        // verify
        assertEquals(expectedResponse, actualResponse);
        assertEquals(expectedResponseValue, actualResponseValue);
    }

    @Test
    @SneakyThrows
    void get_binary_with_single_path_returns_success() {
        // setup
        GlideString key = gs("testKey");
        GlideString path = gs("$");
        CompletableFuture<Object> expectedResponse = new CompletableFuture<>();
        GlideString expectedResponseValue = gs("{\"a\": 1.0, \"b\": 2}");
        expectedResponse.complete(expectedResponseValue);
        when(glideClient.customCommand(eq(new GlideString[] {gs(GlideJson.JSON_GET), key, path})))
                .thenReturn(expectedResponse);

        // exercise
        CompletableFuture<Object> actualResponse = GlideJson.get(glideClient, key, path);
        GlideString actualResponseValue = (GlideString) actualResponse.get();

        // verify
        assertEquals(expectedResponse, actualResponse);
        assertEquals(expectedResponseValue, actualResponseValue);
    }

    @Test
    @SneakyThrows
    void get_with_multiple_paths_returns_success() {
        // setup
        String key = "testKey";
        String path1 = ".firstName";
        String path2 = ".lastName";
        String[] paths = new String[] {path1, path2};
        CompletableFuture<Object> expectedResponse = new CompletableFuture<>();
        String expectedResponseValue = "{\"a\": 1.0, \"b\": 2}";
        expectedResponse.complete(expectedResponseValue);
        when(glideClient.customCommand(eq(new String[] {GlideJson.JSON_GET, key, path1, path2})))
                .thenReturn(expectedResponse);

        // exercise
        CompletableFuture<Object> actualResponse = GlideJson.get(glideClient, key, paths);
        String actualResponseValue = (String) actualResponse.get();

        // verify
        assertEquals(expectedResponse, actualResponse);
        assertEquals(expectedResponseValue, actualResponseValue);
    }

    @Test
    @SneakyThrows
    void get_binary_with_multiple_paths_returns_success() {
        // setup
        GlideString key = gs("testKey");
        GlideString path1 = gs(".firstName");
        GlideString path2 = gs(".lastName");
        GlideString[] paths = new GlideString[] {path1, path2};
        CompletableFuture<Object> expectedResponse = new CompletableFuture<>();
        GlideString expectedResponseValue = gs("{\"a\": 1.0, \"b\": 2}");
        expectedResponse.complete(expectedResponseValue);
        when(glideClient.customCommand(
                        eq(new GlideString[] {gs(GlideJson.JSON_GET), key, path1, path2})))
                .thenReturn(expectedResponse);

        // exercise
        CompletableFuture<Object> actualResponse = GlideJson.get(glideClient, key, paths);
        GlideString actualResponseValue = (GlideString) actualResponse.get();

        // verify
        assertEquals(expectedResponse, actualResponse);
        assertEquals(expectedResponseValue, actualResponseValue);
    }

    @Test
    @SneakyThrows
    void get_with_single_path_and_options_returns_success() {
        // setup
        String key = "testKey";
        String path = "$";
        JsonGetOptions options = JsonGetOptions.builder().indent("\t").space(" ").newline("\n").build();
        CompletableFuture<Object> expectedResponse = new CompletableFuture<>();
        String expectedResponseValue = "{\"a\": 1.0, \"b\": 2}";
        expectedResponse.complete(expectedResponseValue);
        when(glideClient.customCommand(
                        eq(
                                ArrayUtils.add(
                                        ArrayUtils.addAll(new String[] {GlideJson.JSON_GET, key}, options.toArgs()),
                                        path))))
                .thenReturn(expectedResponse);

        // exercise
        CompletableFuture<Object> actualResponse = GlideJson.get(glideClient, key, path, options);
        String actualResponseValue = (String) actualResponse.get();

        // verify
        assertEquals(expectedResponse, actualResponse);
        assertEquals(expectedResponseValue, actualResponseValue);
    }

    @Test
    @SneakyThrows
    void get_binary_with_single_path_and_options_returns_success() {
        // setup
        GlideString key = gs("testKey");
        GlideString path = gs("$");
        JsonGetOptions options = JsonGetOptions.builder().indent("\t").space(" ").newline("\n").build();
        CompletableFuture<Object> expectedResponse = new CompletableFuture<>();
        GlideString expectedResponseValue = gs("{\"a\": 1.0, \"b\": 2}");
        expectedResponse.complete(expectedResponseValue);
        when(glideClient.customCommand(
                        eq(
                                new ArgsBuilder()
                                        .add(new GlideString[] {gs(GlideJson.JSON_GET), key})
                                        .add(options.toArgs())
                                        .add(path)
                                        .toArray())))
                .thenReturn(expectedResponse);

        // exercise
        CompletableFuture<Object> actualResponse = GlideJson.get(glideClient, key, path, options);
        GlideString actualResponseValue = (GlideString) actualResponse.get();

        // verify
        assertEquals(expectedResponse, actualResponse);
        assertEquals(expectedResponseValue, actualResponseValue);
    }

    @Test
    @SneakyThrows
    void get_with_multiple_paths_and_options_returns_success() {
        // setup
        String key = "testKey";
        String path1 = ".firstName";
        String path2 = ".lastName";
        JsonGetOptions options = JsonGetOptions.builder().indent("\t").newline("\n").space(" ").build();
        String[] paths = new String[] {path1, path2};
        CompletableFuture<Object> expectedResponse = new CompletableFuture<>();
        String expectedResponseValue = "{\"a\": 1.0, \"b\": 2}";
        expectedResponse.complete(expectedResponseValue);
        ArrayList<String> argsList = new ArrayList<>();
        argsList.add(GlideJson.JSON_GET);
        argsList.add(key);
        Collections.addAll(argsList, options.toArgs());
        Collections.addAll(argsList, paths);
        when(glideClient.customCommand(eq(argsList.toArray(new String[0]))))
                .thenReturn(expectedResponse);

        // exercise
        CompletableFuture<Object> actualResponse = GlideJson.get(glideClient, key, paths, options);
        String actualResponseValue = (String) actualResponse.get();

        // verify
        assertArrayEquals(
                new String[] {"INDENT", "\t", "NEWLINE", "\n", "SPACE", " "}, options.toArgs());
        assertEquals(expectedResponse, actualResponse);
        assertEquals(expectedResponseValue, actualResponseValue);
    }

    @Test
    @SneakyThrows
    void get_binary_with_multiple_paths_and_options_returns_success() {
        // setup
        GlideString key = gs("testKey");
        GlideString path1 = gs(".firstName");
        GlideString path2 = gs(".lastName");
        JsonGetOptions options = JsonGetOptions.builder().indent("\t").newline("\n").space(" ").build();
        GlideString[] paths = new GlideString[] {path1, path2};
        CompletableFuture<Object> expectedResponse = new CompletableFuture<>();
        GlideString expectedResponseValue = gs("{\"a\": 1.0, \"b\": 2}");
        expectedResponse.complete(expectedResponseValue);
        GlideString[] args =
                new ArgsBuilder()
                        .add(GlideJson.JSON_GET)
                        .add(key)
                        .add(options.toArgs())
                        .add(paths)
                        .toArray();
        when(glideClient.customCommand(eq(args))).thenReturn(expectedResponse);

        // exercise
        CompletableFuture<Object> actualResponse = GlideJson.get(glideClient, key, paths, options);
        GlideString actualResponseValue = (GlideString) actualResponse.get();

        // verify
        assertEquals(expectedResponse, actualResponse);
        assertEquals(expectedResponseValue, actualResponseValue);
    }
}
