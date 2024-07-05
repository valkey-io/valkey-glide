/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.connectors.handlers;

import static glide.api.models.GlideString.gs;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

import glide.api.models.PubSubMessage;
import glide.api.models.configuration.BaseSubscriptionConfiguration;
import glide.api.models.exceptions.RedisException;
import glide.managers.BaseResponseResolver;
import java.util.ArrayList;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

import lombok.SneakyThrows;
import response.ResponseOuterClass;

/** Unit tests for MessageHandler */
public class MessageHandlerTests {
    @Test
    @SneakyThrows
    public void test_exact_message() {
        // Arrange
        BaseResponseResolver fakeResolver =
                new BaseResponseResolver(null) {
                    @Override
                    public Object apply(ResponseOuterClass.Response response) throws RedisException {
                        return Map.of(
                                "kind",
                                MessageHandler.PushKind.Message,
                                "values",
                                new byte[][] {gs("channel").getBytes(), gs("message").getBytes()});
                    }
                };
        MessageHandler handler = new MessageHandler(Optional.empty(), Optional.empty(), fakeResolver);

        // Act.
        handler.handle(null);

        // Assert.
        PubSubMessage expected = new PubSubMessage(gs("message"), gs("channel"));
        assertEquals(expected, handler.peek());
    }

    @Test
    @SneakyThrows
    public void test_exact_message_with_callback() {
        // Arrange
        BaseResponseResolver fakeResolver =
                new BaseResponseResolver(null) {
                    @Override
                    public Object apply(ResponseOuterClass.Response response) throws RedisException {
                        return Map.of(
                                "kind",
                                MessageHandler.PushKind.Message,
                                "values",
                                new byte[][] {gs("channel").getBytes(), gs("message").getBytes()});
                    }
                };
        ArrayList<PubSubMessage> messageList = new ArrayList<>();
        BaseSubscriptionConfiguration.MessageCallback callback =
                (message, context) -> {
                    ArrayList<PubSubMessage> contextAsList = (ArrayList<PubSubMessage>) context;
                    contextAsList.add(message);
                };

        MessageHandler handler =
                new MessageHandler(Optional.of(callback), Optional.of(messageList), fakeResolver);

        // Act.
        handler.handle(null);

        // Assert.
        PubSubMessage expected = new PubSubMessage(gs("message"), gs("channel"));
        assertEquals(1, messageList.size());
        assertEquals(expected, messageList.get(0));
    }

    @Test
    @SneakyThrows
    public void test_sharded_message() {
        // Arrange
        BaseResponseResolver fakeResolver =
                new BaseResponseResolver(null) {
                    @Override
                    public Object apply(ResponseOuterClass.Response response) throws RedisException {
                        return Map.of(
                                "kind",
                                MessageHandler.PushKind.SMessage,
                                "values",
                                new byte[][] {gs("channel").getBytes(), gs("message").getBytes()});
                    }
                };
        MessageHandler handler = new MessageHandler(Optional.empty(), Optional.empty(), fakeResolver);

        // Act.
        handler.handle(null);

        // Assert.
        PubSubMessage expected = new PubSubMessage(gs("message"), gs("channel"));
        assertEquals(expected, handler.peek());
    }

    @Test
    public void test_sharded_message_with_callback() throws Exception {
        // Arrange
        BaseResponseResolver fakeResolver =
                new BaseResponseResolver(null) {
                    @Override
                    public Object apply(ResponseOuterClass.Response response) throws RedisException {
                        return Map.of(
                                "kind",
                                MessageHandler.PushKind.SMessage,
                                "values",
                                new byte[][] {gs("channel").getBytes(), gs("message").getBytes()});
                    }
                };
        ArrayList<PubSubMessage> messageList = new ArrayList<>();
        BaseSubscriptionConfiguration.MessageCallback callback =
                (message, context) -> {
                    ArrayList<PubSubMessage> contextAsList = (ArrayList<PubSubMessage>) context;
                    contextAsList.add(message);
                };

        MessageHandler handler =
                new MessageHandler(Optional.of(callback), Optional.of(messageList), fakeResolver);

        // Act.
        handler.handle(null);

        // Assert.
        PubSubMessage expected = new PubSubMessage(gs("message"), gs("channel"));
        assertEquals(1, messageList.size());
        assertEquals(expected, messageList.get(0));
    }

    @Test
    @SneakyThrows
    public void test_pattern_message() { // Arrange
        BaseResponseResolver fakeResolver =
                new BaseResponseResolver(null) {
                    @Override
                    public Object apply(ResponseOuterClass.Response response) throws RedisException {
                        return Map.of(
                                "kind",
                                MessageHandler.PushKind.PMessage,
                                "values",
                                new byte[][] {
                                    gs("pattern").getBytes(), gs("channel").getBytes(), gs("message").getBytes()
                                });
                    }
                };
        MessageHandler handler = new MessageHandler(Optional.empty(), Optional.empty(), fakeResolver);

        // Act.
        handler.handle(null);

        // Assert.
        PubSubMessage expected = new PubSubMessage(gs("message"), gs("channel"), gs("pattern"));
        assertEquals(expected, handler.peek());
    }

    @Test
    @SneakyThrows
    public void test_pattern_message_with_callback() {
        // Arrange
        BaseResponseResolver fakeResolver =
                new BaseResponseResolver(null) {
                    @Override
                    public Object apply(ResponseOuterClass.Response response) throws RedisException {
                        return Map.of(
                                "kind",
                                MessageHandler.PushKind.PMessage,
                                "values",
                                new byte[][] {
                                    gs("pattern").getBytes(), gs("channel").getBytes(), gs("message").getBytes()
                                });
                    }
                };
        ArrayList<PubSubMessage> messageList = new ArrayList<>();
        BaseSubscriptionConfiguration.MessageCallback callback =
                (message, context) -> {
                    ArrayList<PubSubMessage> contextAsList = (ArrayList<PubSubMessage>) context;
                    contextAsList.add(message);
                };

        MessageHandler handler =
                new MessageHandler(Optional.of(callback), Optional.of(messageList), fakeResolver);

        // Act.
        handler.handle(null);

        // Assert.
        PubSubMessage expected = new PubSubMessage(gs("message"), gs("channel"), gs("pattern"));
        assertEquals(1, messageList.size());
        assertEquals(expected, messageList.get(0));
    }

    @Test
    public void test_exception_from_callback_wrapped() throws Exception {
        // Arrange
        BaseResponseResolver fakeResolver =
                new BaseResponseResolver(null) {
                    @Override
                    public Object apply(ResponseOuterClass.Response response) throws RedisException {
                        return Map.of(
                                "kind",
                                MessageHandler.PushKind.Message,
                                "values",
                                new byte[][] {gs("channel").getBytes(), gs("message").getBytes()});
                    }
                };
        ArrayList<PubSubMessage> messageList = new ArrayList<>();
        BaseSubscriptionConfiguration.MessageCallback callback =
                (message, context) -> {
                    throw new RuntimeException(message.getMessage().getString());
                };

        MessageHandler handler =
                new MessageHandler(Optional.of(callback), Optional.of(messageList), fakeResolver);

        // Act.
        MessageHandler.MessageCallbackException ex =
                assertThrows(MessageHandler.MessageCallbackException.class, () -> handler.handle(null));

        // Assert.
        assertInstanceOf(RuntimeException.class, ex.getCause());
        assertEquals(new RuntimeException("message").getMessage(), ex.getCause().getMessage());
    }
}
