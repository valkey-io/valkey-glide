package glide.models;

import connection_request.ConnectionRequestOuterClass.ConnectionRequest;
import connection_request.ConnectionRequestOuterClass.NodeAddress;
import connection_request.ConnectionRequestOuterClass.ReadFrom;
import connection_request.ConnectionRequestOuterClass.TlsMode;
import glide.connectors.handlers.CallbackDispatcher;
import glide.managers.CommandManager;
import glide.managers.ConnectionManager;
import java.util.List;
import redis_request.RedisRequestOuterClass.Command;
import redis_request.RedisRequestOuterClass.Command.ArgsArray;
import redis_request.RedisRequestOuterClass.RedisRequest;
import redis_request.RedisRequestOuterClass.RequestType;
import redis_request.RedisRequestOuterClass.Routes;
import redis_request.RedisRequestOuterClass.SimpleRoutes;

public class RequestBuilder {

    /**
     * Build a protobuf connection request.<br>
     * Used by {@link ConnectionManager#connectToRedis}.
     */
    // TODO support more parameters and/or configuration object
    public static ConnectionRequest createConnectionRequest(
            String host, int port, boolean useSsl, boolean clusterMode) {
        return ConnectionRequest.newBuilder()
                .addAddresses(NodeAddress.newBuilder().setHost(host).setPort(port).build())
                .setTlsMode(useSsl ? TlsMode.SecureTls : TlsMode.NoTls)
                .setClusterModeEnabled(clusterMode)
                .setReadFrom(ReadFrom.Primary)
                .setDatabaseId(0)
                .build();
    }

    /**
     * Build a protobuf command/transaction request draft.<br>
     * Used by {@link CommandManager}.
     *
     * @return An uncompleted request. {@link CallbackDispatcher} is responsible to complete it by
     *     adding a callback id.
     */
    public static RedisRequest.Builder prepareRedisRequest(RequestType command, List<String> args) {
        var commandArgs = ArgsArray.newBuilder();
        for (var arg : args) {
            commandArgs.addArgs(arg);
        }

        return RedisRequest.newBuilder()
                .setSingleCommand(
                        Command.newBuilder().setRequestType(command).setArgsArray(commandArgs.build()).build())
                .setRoute(Routes.newBuilder().setSimpleRoutes(SimpleRoutes.AllNodes).build());
    }
}
