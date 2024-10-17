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
import glide.api.models.commands.json.JsonGetOptionsBinary;
import glide.utils.ArgsBuilder;
import glide.utils.ArrayTransformUtils;
import java.util.ArrayList;
import java.util.Collections;
import java.util.concurrent.CompletableFuture;
import lombok.SneakyThrows;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class JsonTest {

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
                        .customCommand(eq(new String[] {Json.JSON_SET, key, path, jsonValue}))
                        .<String>thenApply(any()))
                .thenReturn(expectedResponse);

        // exercise
        CompletableFuture<String> actualResponse = Json.set(glideClient, key, path, jsonValue);
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
                        .customCommand(eq(new GlideString[] {gs(Json.JSON_SET), key, path, jsonValue}))
                        .<String>thenApply(any()))
                .thenReturn(expectedResponse);

        // exercise
        CompletableFuture<String> actualResponse = Json.set(glideClient, key, path, jsonValue);
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
                                eq(new String[] {Json.JSON_SET, key, path, jsonValue, setCondition.getValkeyApi()}))
                        .<String>thenApply(any()))
                .thenReturn(expectedResponse);

        // exercise
        CompletableFuture<String> actualResponse =
                Json.set(glideClient, key, path, jsonValue, setCondition);
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
                                            gs(Json.JSON_SET), key, path, jsonValue, gs(setCondition.getValkeyApi())
                                        }))
                        .<String>thenApply(any()))
                .thenReturn(expectedResponse);

        // exercise
        CompletableFuture<String> actualResponse =
                Json.set(glideClient, key, path, jsonValue, setCondition);
        String actualResponseValue = actualResponse.get();

        // verify
        assertEquals(expectedResponse, actualResponse);
        assertEquals(expectedResponseValue, actualResponseValue);
    }

    @Test
    @SneakyThrows
    void get_with_no_path_returns_success() {
        // setup
        String key = "testKey";
        CompletableFuture<String> expectedResponse = new CompletableFuture<>();
        String expectedResponseValue = "{\"a\": 1.0, \"b\": 2}";
        expectedResponse.complete(expectedResponseValue);
        when(glideClient.customCommand(eq(new String[] {Json.JSON_GET, key})).<String>thenApply(any()))
                .thenReturn(expectedResponse);

        // exercise
        CompletableFuture<String> actualResponse = Json.get(glideClient, key);
        String actualResponseValue = actualResponse.get();

        // verify
        assertEquals(expectedResponse, actualResponse);
        assertEquals(expectedResponseValue, actualResponseValue);
    }

    @Test
    @SneakyThrows
    void get_binary_with_no_path_returns_success() {
        // setup
        GlideString key = gs("testKey");
        CompletableFuture<GlideString> expectedResponse = new CompletableFuture<>();
        GlideString expectedResponseValue = gs("{\"a\": 1.0, \"b\": 2}");
        expectedResponse.complete(expectedResponseValue);
        when(glideClient
                        .customCommand(eq(new GlideString[] {gs(Json.JSON_GET), key}))
                        .<GlideString>thenApply(any()))
                .thenReturn(expectedResponse);

        // exercise
        CompletableFuture<GlideString> actualResponse = Json.get(glideClient, key);
        GlideString actualResponseValue = actualResponse.get();

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
        CompletableFuture<String> expectedResponse = new CompletableFuture<>();
        String expectedResponseValue = "{\"a\": 1.0, \"b\": 2}";
        expectedResponse.complete(expectedResponseValue);
        when(glideClient
                        .customCommand(eq(new String[] {Json.JSON_GET, key, path1, path2}))
                        .<String>thenApply(any()))
                .thenReturn(expectedResponse);

        // exercise
        CompletableFuture<String> actualResponse = Json.get(glideClient, key, paths);
        String actualResponseValue = actualResponse.get();

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
        CompletableFuture<GlideString> expectedResponse = new CompletableFuture<>();
        GlideString expectedResponseValue = gs("{\"a\": 1.0, \"b\": 2}");
        expectedResponse.complete(expectedResponseValue);
        when(glideClient
                        .customCommand(eq(new GlideString[] {gs(Json.JSON_GET), key, path1, path2}))
                        .<GlideString>thenApply(any()))
                .thenReturn(expectedResponse);

        // exercise
        CompletableFuture<GlideString> actualResponse = Json.get(glideClient, key, paths);
        GlideString actualResponseValue = actualResponse.get();

        // verify
        assertEquals(expectedResponse, actualResponse);
        assertEquals(expectedResponseValue, actualResponseValue);
    }

    @Test
    @SneakyThrows
    void get_with_no_path_and_options_returns_success() {
        // setup
        String key = "testKey";
        JsonGetOptions options = JsonGetOptions.builder().indent("\t").space(" ").newline("\n").build();
        CompletableFuture<String> expectedResponse = new CompletableFuture<>();
        String expectedResponseValue = "{\"a\": 1.0, \"b\": 2}";
        expectedResponse.complete(expectedResponseValue);
        when(glideClient
                        .customCommand(
                                eq(
                                        ArrayTransformUtils.concatenateArrays(
                                                new String[] {Json.JSON_GET, key}, options.toArgs())))
                        .<String>thenApply(any()))
                .thenReturn(expectedResponse);

        // exercise
        CompletableFuture<String> actualResponse = Json.get(glideClient, key, options);
        String actualResponseValue = actualResponse.get();

        // verify
        assertEquals(expectedResponse, actualResponse);
        assertEquals(expectedResponseValue, actualResponseValue);
    }

    @Test
    @SneakyThrows
    void get_binary_with_no_path_and_options_returns_success() {
        // setup
        GlideString key = gs("testKey");
        JsonGetOptionsBinary options =
                JsonGetOptionsBinary.builder().indent(gs("\t")).space(gs(" ")).newline(gs("\n")).build();
        CompletableFuture<GlideString> expectedResponse = new CompletableFuture<>();
        GlideString expectedResponseValue = gs("{\"a\": 1.0, \"b\": 2}");
        expectedResponse.complete(expectedResponseValue);
        when(glideClient
                        .customCommand(
                                eq(
                                        new ArgsBuilder()
                                                .add(new GlideString[] {gs(Json.JSON_GET), key})
                                                .add(options.toArgs())
                                                .toArray()))
                        .<GlideString>thenApply(any()))
                .thenReturn(expectedResponse);

        // exercise
        CompletableFuture<GlideString> actualResponse = Json.get(glideClient, key, options);
        GlideString actualResponseValue = actualResponse.get();

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
        CompletableFuture<String> expectedResponse = new CompletableFuture<>();
        String expectedResponseValue = "{\"a\": 1.0, \"b\": 2}";
        expectedResponse.complete(expectedResponseValue);
        ArrayList<String> argsList = new ArrayList<>();
        argsList.add(Json.JSON_GET);
        argsList.add(key);
        Collections.addAll(argsList, options.toArgs());
        Collections.addAll(argsList, paths);
        when(glideClient.customCommand(eq(argsList.toArray(new String[0]))).<String>thenApply(any()))
                .thenReturn(expectedResponse);

        // exercise
        CompletableFuture<String> actualResponse = Json.get(glideClient, key, paths, options);
        String actualResponseValue = actualResponse.get();

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
        JsonGetOptionsBinary options =
                JsonGetOptionsBinary.builder().indent(gs("\t")).newline(gs("\n")).space(gs(" ")).build();
        GlideString[] paths = new GlideString[] {path1, path2};
        CompletableFuture<GlideString> expectedResponse = new CompletableFuture<>();
        GlideString expectedResponseValue = gs("{\"a\": 1.0, \"b\": 2}");
        expectedResponse.complete(expectedResponseValue);
        GlideString[] args =
                new ArgsBuilder().add(Json.JSON_GET).add(key).add(options.toArgs()).add(paths).toArray();
        when(glideClient.customCommand(eq(args)).<GlideString>thenApply(any()))
                .thenReturn(expectedResponse);

        // exercise
        CompletableFuture<GlideString> actualResponse = Json.get(glideClient, key, paths, options);
        GlideString actualResponseValue = actualResponse.get();

        // verify
        assertEquals(expectedResponse, actualResponse);
        assertEquals(expectedResponseValue, actualResponseValue);
    }
}
