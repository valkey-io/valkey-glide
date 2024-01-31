/** Copyright GLIDE-for-Redis Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.api.commands;

import java.util.concurrent.CompletableFuture;

/** Base Commands interface to handle generic command and transaction requests. */
public interface BaseCommands {

    /**
     * Executes a single command, without checking inputs. Every part of the command, including
     * subcommands, should be added as a separate value in args.
     *
     * @remarks This function should only be used for single-response commands. Commands that don't
     *     return response (such as SUBSCRIBE), or that return potentially more than a single response
     *     (such as XREAD), or that change the client's behavior (such as entering pub/sub mode on
     *     RESP2 connections) shouldn't be called using this function.
     * @example Returns a list of all pub/sub clients:
     *     <pre>
     * Object result = client.customCommand(new String[]{"CLIENT","LIST","TYPE", "PUBSUB"}).get();
     * </pre>
     *
     * @param args arguments for the custom command
     * @return a CompletableFuture with response result from Redis
     */
    CompletableFuture<Object> customCommand(String[] args);
}
