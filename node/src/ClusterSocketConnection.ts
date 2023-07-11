import * as net from "net";
import { connection_request } from "./ProtobufMessage";
import { ConnectionOptions, SocketConnection } from "./SocketConnection";

export type Routes =
    | "all_primaries"
    | "all_nodes"
    | "multi_shard"
    | "random"
    | "master_slot"
    | "replica_slot";
export class ClusterSocketConnection extends SocketConnection {
    protected createConnectionRequest(
        options: ConnectionOptions
    ): connection_request.IConnectionRequest {
        const configuration = super.createConnectionRequest(options);
        configuration.clusterModeEnabled = true;
        return configuration;
    }

    public static async CreateConnection(
        options: ConnectionOptions
    ): Promise<ClusterSocketConnection> {
        return await super.CreateConnectionInternal(
            options,
            (socket: net.Socket, options?: ConnectionOptions) =>
                new ClusterSocketConnection(socket, options)
        );
    }

    /// Returns PONG if no argument is provided, otherwise return a copy of the argument
    /// See https://redis.io/commands/ping/ for details.
    // TODO: implement routing
    public ping(
        str?: string,
        route: Routes = "all_primaries"
    ): Promise<string> {
        return super.ping(str);
    }
}
