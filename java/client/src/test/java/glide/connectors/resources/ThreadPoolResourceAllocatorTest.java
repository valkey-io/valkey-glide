package glide.connectors.resources;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.internal.verification.VerificationModeFactory.times;

import java.util.function.Supplier;
import org.junit.jupiter.api.Test;

public class ThreadPoolResourceAllocatorTest {

    ThreadPoolResourceAllocator service;

    @Test
    public void getOrCreateReturnsDefault() {
        ThreadPoolResource mockedThreadPool = mock(ThreadPoolResource.class);
        Supplier<ThreadPoolResource> threadPoolSupplier = mock(Supplier.class);
        when(threadPoolSupplier.get()).thenReturn(mockedThreadPool);

        ThreadPoolResource theResource = service.getOrCreate(threadPoolSupplier);
        assertEquals(mockedThreadPool, theResource);

        // Ensure that supplier only is invoked once to set up the shared resource
        ThreadPoolResource theSameResource = service.getOrCreate(threadPoolSupplier);
        assertEquals(mockedThreadPool, theSameResource);
        verify(threadPoolSupplier, times(1)).get();
    }
}
