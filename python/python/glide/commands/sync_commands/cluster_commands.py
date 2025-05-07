from typing import List, Optional, cast

from glide.commands.core_options import InfoSection
from glide.commands.sync_commands.core import CoreCommands
from glide.constants import TClusterResponse, TEncodable, TResult
from glide.protobuf.command_request_pb2 import RequestType
from glide.routes import Route


class ClusterCommands(CoreCommands):
    def custom_command(
        self, command_args: List[TEncodable], route: Optional[Route] = None
    ) -> TClusterResponse[TResult]:
        """
        Executes a single command, without checking inputs.
        See the [Valkey GLIDE Wiki](https://github.com/valkey-io/valkey-glide/wiki/General-Concepts#custom-command)
        for details on the restrictions and limitations of the custom command API.

            For example - Return a list of all pub/sub clients from all nodes::

                connection.customCommand(["CLIENT", "LIST","TYPE", "PUBSUB"], AllNodes())

        Args:
            command_args (List[TEncodable]): List of the command's arguments, where each argument is either a string or bytes.
            Every part of the command, including the command name and subcommands, should be added as a separate value in args.
            route (Optional[Route]): The command will be routed automatically based on the passed command's default request
                policy, unless `route` is provided, in which
            case the client will route the command to the nodes defined by `route`. Defaults to None.

        Returns:
            TClusterResponse[TResult]: The returning value depends on the executed command and the route.
        """
        return cast(
            TClusterResponse[TResult],
            self._execute_command(RequestType.CustomCommand, command_args, route),
        )

    def info(
        self,
        sections: Optional[List[InfoSection]] = None,
        route: Optional[Route] = None,
    ) -> TClusterResponse[bytes]:
        """
        Get information and statistics about the server.

        See [valkey.io](https://valkey.io/commands/info/) for details.

        Args:
            sections (Optional[List[InfoSection]]): A list of InfoSection values specifying which sections of
                information to retrieve. When no parameter is provided, the default option is assumed.
            route (Optional[Route]): The command will be routed to all primaries, unless `route` is provided, in which
                case the client will route the command to the nodes defined by `route`. Defaults to None.

        Returns:
            TClusterResponse[bytes]: If a single node route is requested, returns a bytes string containing the information for
            the required sections. Otherwise, returns a dict of bytes strings, with each key containing the address of
            the queried node and value containing the information regarding the requested sections.
        """
        args: List[TEncodable] = (
            [section.value for section in sections] if sections else []
        )
        return cast(
            TClusterResponse[bytes],
            self._execute_command(RequestType.Info, args, route),
        )

    def echo(
        self, message: TEncodable, route: Optional[Route] = None
    ) -> TClusterResponse[bytes]:
        """
        Echoes the provided `message` back.

        See [valkey.io](https://valkey.io/commands/echo) for more details.

        Args:
            message (TEncodable): The message to be echoed back.
            route (Optional[Route]): The command will be routed to a random node, unless `route` is provided,
                in which case the client will route the command to the nodes defined by `route`.

        Returns:
            TClusterResponse[bytes]: The provided `message`.

            When specifying a route other than a single node, response will be::

                {Address (bytes) : response (bytes) , ... }

            with type of Dict[bytes, bytes].

        Examples:
            >>> client.echo(b"Valkey GLIDE")
                b'Valkey GLIDE'
            >>> client.echo("Valkey GLIDE", AllNodes())
                {b'addr': b'Valkey GLIDE', b'addr2': b'Valkey GLIDE', b'addr3': b'Valkey GLIDE'}
        """
        return cast(
            TClusterResponse[bytes],
            self._execute_command(RequestType.Echo, [message], route),
        )
