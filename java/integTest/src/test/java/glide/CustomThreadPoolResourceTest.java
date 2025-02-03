/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide;

import static glide.TestUtilities.commonClientConfig;
import static org.junit.jupiter.api.Assertions.assertEquals;

import glide.api.GlideClient;
import glide.api.models.configuration.ThreadPoolResource;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.epoll.EpollDomainSocketChannel;
import io.netty.channel.epoll.EpollEventLoopGroup;
import io.netty.channel.kqueue.KQueueDomainSocketChannel;
import io.netty.channel.kqueue.KQueueEventLoopGroup;
import java.util.concurrent.TimeUnit;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;

public class CustomThreadPoolResourceTest {

    class KQueueResource extends ThreadPoolResource {
        public KQueueResource() {
            super((EventLoopGroup) new KQueueEventLoopGroup(numOfThreads), KQueueDomainSocketChannel.class);
        }
    }

    class EPollResource extends ThreadPoolResource {
        public EPollResource() {
            super((EventLoopGroup) new EpollEventLoopGroup(numOfThreads), EpollDomainSocketChannel.class);
        }
    }

    private static final int numOfThreads = 8;

    @Test
    @SneakyThrows
    public void standalone_client_with_custom_threadPoolResource() {
        ThreadPoolResource customThreadPoolResource;

        if (System.getProperty("os.name").startsWith("Mac")) {
            customThreadPoolResource = new KQueueResource();
        } else if (System.getProperty("os.name").startsWith("Linux")) {
            customThreadPoolResource = new EPollResource();
        } else {
            throw new RuntimeException("Current platform supports no known thread pool resources");
        }

        var regularClient =
                GlideClient.createClient(commonClientConfig().threadPoolResource(customThreadPoolResource).build())
                        .get(10, TimeUnit.SECONDS);

        String payload = (String) regularClient.customCommand(new String[] {"PING"}).get();
        assertEquals("PONG", payload);

        regularClient.close();
        customThreadPoolResource.getEventLoopGroup().shutdownGracefully();
    }
}
