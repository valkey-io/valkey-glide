from typing import List, Optional

from pybushka.async_commands.core import CoreCommands, InfoSection
from pybushka.constants import TResult
from pybushka.protobuf.redis_request_pb2 import RequestType


class CMDCommands(CoreCommands):
    async def custom_command(self, command_args: List[str]) -> TResult:
        """Executes a single command, without checking inputs.
            @example - Return a list of all pub/sub clients:

                connection.customCommand(["CLIENT", "LIST","TYPE", "PUBSUB"])
        Args:
            command_args (List[str]): List of strings of the command's arguements.
            Every part of the command, including the command name and subcommands, should be added as a separate value in args.

        Returns:
            TResult: The returning value depends on the executed command and the route
        """
        return await self._execute_command(RequestType.CustomCommand, command_args)

    async def info(
        self,
        sections: Optional[List[InfoSection]] = None,
    ) -> str:
        """Get information and statistics about the Redis server.
        See https://redis.io/commands/info/ for details.

        Args:
            sections (Optional[List[InfoSection]]): A list of InfoSection values specifying which sections of
            information to retrieve. When no parameter is provided, the default option is assumed.


        Returns:
            str: Returns a string containing the information for the sections requested.
        """
        args = [section.value for section in sections] if sections else []
        return await self._execute_command(RequestType.Info, args)
