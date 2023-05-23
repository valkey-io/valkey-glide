import { ConnectionOptions, SocketConnection } from "..";
import { connection_request } from "./ProtobufMessage";
import * as net from "net";

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
            (socket: net.Socket) => new ClusterSocketConnection(socket)
        );
    }
}
