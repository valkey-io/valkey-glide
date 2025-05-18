from typing import List, Optional, cast

from glide.shared.commands.core_options import InfoSection
from .core import CoreCommands
from glide.shared.constants import TEncodable, TResult
from .protobuf.command_request_pb2 import RequestType


class StandaloneCommands(CoreCommands):
    def custom_command(self, command_args: List[TEncodable]) -> TResult:
        """
        Executes a single command, without checking inputs.
        See the [Valkey GLIDE Wiki](https://github.com/valkey-io/valkey-glide/wiki/General-Concepts#custom-command)
        for details on the restrictions and limitations of the custom command API.
        Args:
            command_args (List[TEncodable]): List of the command's arguments, where each argument is either a string or bytes.
            Every part of the command, including the command name and subcommands, should be added as a separate value in args.
        Returns:
            TResult: The returning value depends on the executed command.
        Example:
            >>> connection.customCommand(["CLIENT", "LIST","TYPE", "PUBSUB"])
        """
        return self._execute_command(RequestType.CustomCommand, command_args)

    def info(
        self,
        sections: Optional[List[InfoSection]] = None,
    ) -> bytes:
        """
        Get information and statistics about the server.

        See [valkey.io](https://valkey.io/commands/info/) for details.

        Args:
            sections (Optional[List[InfoSection]]): A list of InfoSection values specifying which sections of
                information to retrieve. When no parameter is provided, the default option is assumed.

        Returns:
            bytes: Returns bytes containing the information for the sections requested.
        """
        args: List[TEncodable] = (
            [section.value for section in sections] if sections else []
        )
        return cast(bytes, self._execute_command(RequestType.Info, args))
