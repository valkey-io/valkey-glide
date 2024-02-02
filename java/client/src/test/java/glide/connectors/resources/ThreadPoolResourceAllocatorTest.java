/** Copyright GLIDE-for-Redis Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.connectors.resources;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.internal.verification.VerificationModeFactory.times;

import io.netty.channel.EventLoop;
import io.netty.channel.EventLoopGroup;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class ThreadPoolResourceAllocatorTest {

    ThreadPoolResourceAllocator service;

    @BeforeEach
    public void init() {
        var threadPoolResource = ThreadPoolResourceAllocator.getOrCreate(() -> null);
        if (threadPoolResource != null) {
            threadPoolResource.getEventLoopGroup().shutdownGracefully();
            ThreadPoolResourceAllocator.getOrCreate(() -> null);
        }
    }

    @Test
    public void getOrCreate_returns_default_after_repeated_calls() {
        ThreadPoolResource mockedThreadPoolResource = mock(ThreadPoolResource.class);
        EventLoopGroup mockedEventLoopGroup = mock(EventLoop.class);
        Supplier<ThreadPoolResource> threadPoolSupplier = mock(Supplier.class);

        when(mockedThreadPoolResource.getEventLoopGroup()).thenReturn(mockedEventLoopGroup);
        when(mockedEventLoopGroup.isShuttingDown()).thenReturn(false);
        when(threadPoolSupplier.get()).thenReturn(mockedThreadPoolResource);

        ThreadPoolResource theResource = service.getOrCreate(threadPoolSupplier);
        assertEquals(mockedThreadPoolResource, theResource);

        // Ensure that supplier only is invoked once to set up the shared resource
        ThreadPoolResource theSameResource = service.getOrCreate(threadPoolSupplier);
        assertEquals(mockedThreadPoolResource, theSameResource);
        verify(threadPoolSupplier, times(1)).get();

        // teardown
        when(mockedEventLoopGroup.isShuttingDown()).thenReturn(true);
    }

    @Test
    public void getOrCreate_returns_new_thread_pool_after_shutdown() {
        ThreadPoolResource mockedThreadPoolResource = mock(ThreadPoolResource.class);
        EventLoopGroup mockedEventLoopGroup = mock(EventLoop.class);
        Supplier<ThreadPoolResource> threadPoolSupplier = mock(Supplier.class);

        when(mockedThreadPoolResource.getEventLoopGroup()).thenReturn(mockedEventLoopGroup);
        when(mockedEventLoopGroup.isShuttingDown()).thenReturn(true);
        when(threadPoolSupplier.get()).thenReturn(mockedThreadPoolResource);

        ThreadPoolResource theResource = service.getOrCreate(threadPoolSupplier);
        assertEquals(mockedThreadPoolResource, theResource);

        // Ensure that supplier only is invoked once to set up the shared resource
        ThreadPoolResource theSameResource = service.getOrCreate(threadPoolSupplier);
        assertEquals(mockedThreadPoolResource, theSameResource);
        verify(threadPoolSupplier, times(2)).get();

        // teardown
        when(mockedEventLoopGroup.isShuttingDown()).thenReturn(true);
    }
}
