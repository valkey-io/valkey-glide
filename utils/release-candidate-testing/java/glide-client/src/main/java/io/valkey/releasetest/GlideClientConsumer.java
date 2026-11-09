/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package io.valkey.releasetest;

import static java.util.concurrent.TimeUnit.SECONDS;

import glide.api.GlideClient;
import glide.api.models.configuration.GlideClientConfiguration;
import glide.api.models.configuration.NodeAddress;

public final class GlideClientConsumer {
    private GlideClientConsumer() {}

    public static void main(String[] args) throws Exception {
        if (args.length != 2) {
            throw new IllegalArgumentException("Expected host and port arguments");
        }

        GlideClientConfiguration config =
                GlideClientConfiguration.builder()
                        .address(
                                NodeAddress.builder()
                                        .host(args[0])
                                        .port(Integer.parseInt(args[1]))
                                        .build())
                        .build();

        try (GlideClient client = GlideClient.createClient(config).get(10, SECONDS)) {
            String response = client.ping().get(10, SECONDS);
            if (!"PONG".equals(response)) {
                throw new IllegalStateException("Expected PONG, received " + response);
            }
            System.out.println("Received PONG from the published GLIDE Java client");
        }
    }
}
