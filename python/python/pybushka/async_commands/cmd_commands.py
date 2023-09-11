from typing import List, Optional, cast

from pybushka.async_commands.core import BaseTransaction, CoreCommands, InfoSection
from pybushka.constants import TOK, TResult
from pybushka.protobuf.redis_request_pb2 import RequestType


class Transaction(BaseTransaction):
    """
    Extends BaseTransaction class for standalone Redis commands.
    """

    # TODO: add MOVE, SLAVEOF and all SENTINEL commands
    def select(self, index: int):
        """Change the currently selected Redis database.
        See https://redis.io/commands/select/ for details.

        Args:
            index (int): The index of the database to select.

        Command response:
            A simple OK response.
        """
        self.append_command(RequestType.Select, [str(index)])


class CMDCommands(CoreCommands):
    async def custom_command(self, command_args: List[str]) -> TResult:
        """Executes a single command, without checking inputs.
            @example - Return a list of all pub/sub clients:

                connection.customCommand(["CLIENT", "LIST","TYPE", "PUBSUB"])
        Args:
            command_args (List[str]): List of strings of the command's arguments.
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
        return cast(str, await self._execute_command(RequestType.Info, args))

    async def exec(
        self,
        transaction: BaseTransaction | Transaction,
    ) -> List[TResult]:
        """Execute a transaction by processing the queued commands.
        See https://redis.io/topics/Transactions/ for details on Redis Transactions.

        Args:
            transaction (Transaction): A Transaction object containing a list of commands to be executed.

        Returns:
            List[TResult]: A list of results corresponding to the execution of each command
            in the transaction. If a command returns a value, it will be included in the list. If a command
            doesn't return a value, the list entry will be None.
        """
        commands = transaction.commands[:]
        return await self.execute_transaction(commands)

    async def select(self, index: int) -> TOK:
        """Change the currently selected Redis database.
        See https://redis.io/commands/select/ for details.

        Args:
            index (int): The index of the database to select.

        Returns:
            A simple OK response.
        """
        return cast(TOK, await self._execute_command(RequestType.Select, [str(index)]))

    async def config_resetstat(self) -> TOK:
        """Reset the statistics reported by Redis.
        See https://redis.io/commands/config-resetstat/ for details.
        Returns:
            OK: Returns "OK" to confirm that the statistics were successfully reset.
        """
        return cast(TOK, await self._execute_command(RequestType.ConfigResetStat, []))

    async def config_rewrite(self) -> TOK:
        """Rewrite the configuration file with the current configuration.
        See https://redis.io/commands/config-rewrite/ for details.

        Returns:
            OK: OK is returned when the configuration was rewritten properly. Otherwise an error is returned.
        """
        return cast(TOK, await self._execute_command(RequestType.ConfigRewrite, []))

    async def client_id(
        self,
    ) -> int:
        """Returns the current connection id.
        See https://redis.io/commands/client-id/ for more information.

        Returns:
            int: the id of the client.
        """
        return cast(int, await self._execute_command(RequestType.ClientId, []))
