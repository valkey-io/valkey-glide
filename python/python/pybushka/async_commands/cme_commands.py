from typing import List, Optional, Union

from pybushka.async_commands.core import CoreCommands, InfoSection
from pybushka.constants import TResult
from pybushka.protobuf.redis_request_pb2 import RequestType
from pybushka.routes import TRoute


class CMECommands(CoreCommands):
    async def custom_command(
        self, command_args: List[str], route: Optional[TRoute] = None
    ) -> TResult:
        """Executes a single command, without checking inputs.
            @example - Return a list of all pub/sub clients from all nodes:

                connection.customCommand(["CLIENT", "LIST","TYPE", "PUBSUB"], AllNodes())
        Args:
            command_args (List[str]): List of strings of the command's arguements.
            Every part of the command, including the command name and subcommands, should be added as a separate value in args.
            route (Optional[TRoute], optional): The command will be routed automatically, unless `route` is provided, in which
            case the client will initially try to route the command to the nodes defined by `route`. Defaults to None.

        Returns:
            TResult: The returning value depends on the executed command and the route
        """
        return await self._execute_command(
            RequestType.CustomCommand, command_args, route
        )

    async def info(
        self,
        sections: Optional[List[InfoSection]] = None,
        route: Optional[TRoute] = None,
    ) -> Union[List[str], str]:
        args = [section.value for section in sections] if sections else []
        return await self._execute_command(RequestType.Info, args, route)
