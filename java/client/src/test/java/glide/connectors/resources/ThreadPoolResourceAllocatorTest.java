package glide.connectors.resources;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.internal.verification.VerificationModeFactory.times;

import io.netty.channel.EventLoopGroup;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;

public class ThreadPoolResourceAllocatorTest {

    ThreadPoolResourceAllocator service;

    @Test
    public void getOrCreateReturnsDefault() {
        new ThreadPoolResourceAllocator.ShutdownHook().run();

        ThreadPoolResource mockedThreadPool = mock(ThreadPoolResource.class);
        Supplier<ThreadPoolResource> threadPoolSupplier = mock(Supplier.class);
        when(threadPoolSupplier.get()).thenReturn(mockedThreadPool);

        ThreadPoolResource theResource = service.getOrCreate(threadPoolSupplier);
        assertEquals(mockedThreadPool, theResource);

        ThreadPoolResource theSameResource = service.getOrCreate(threadPoolSupplier);
        assertEquals(mockedThreadPool, theSameResource);

        // and test that the supplier isn't used any longer once the default is set
        Supplier<ThreadPoolResource> notUsedSupplier = mock(Supplier.class);
        ThreadPoolResource firstResource = service.getOrCreate(notUsedSupplier);
        verify(notUsedSupplier, times(0)).get();
        assertEquals(mockedThreadPool, firstResource);

        // teardown
        // remove the mocked resource
        EventLoopGroup mockedELG = mock(EventLoopGroup.class);
        when(mockedThreadPool.getEventLoopGroup()).thenReturn(mockedELG);
        new ThreadPoolResourceAllocator.ShutdownHook().run();
    }
}
