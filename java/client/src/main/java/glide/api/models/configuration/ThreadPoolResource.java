/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.api.models.configuration;

import io.netty.channel.EventLoopGroup;
import io.netty.channel.unix.DomainSocketChannel;
import lombok.Getter;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

/**
 * Abstract base class that provides the EventLoopGroup and DomainSocketChannel to be used by the
 * Netty protocol.
 */
@Getter
@RequiredArgsConstructor
public abstract class ThreadPoolResource {
    private final EventLoopGroup eventLoopGroup;
    private final Class<? extends DomainSocketChannel> domainSocketChannelClass;
}
