from typing import List, Optional, Union, cast

from pybushka.async_commands.core import BaseTransaction, CoreCommands, InfoSection
from pybushka.constants import TOK, TResult
from pybushka.protobuf.redis_request_pb2 import RequestType
from pybushka.routes import Route


class ClusterTransaction(BaseTransaction):
    """
    Extends BaseTransaction class for cluster mode commands.
    """

    # TODO: add all CLUSTER commands
    pass


class CMECommands(CoreCommands):
    async def custom_command(
        self, command_args: List[str], route: Optional[Route] = None
    ) -> TResult:
        """Executes a single command, without checking inputs.
            @example - Return a list of all pub/sub clients from all nodes:

                connection.customCommand(["CLIENT", "LIST","TYPE", "PUBSUB"], AllNodes())
        Args:
            command_args (List[str]): List of strings of the command's arguments.
            Every part of the command, including the command name and subcommands, should be added as a separate value in args.
            route (Optional[Route]): The command will be routed automatically, unless `route` is provided, in which
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
        route: Optional[Route] = None,
    ) -> Union[List[List[str]], str]:
        """Get information and statistics about the Redis server.
        See https://redis.io/commands/info/ for details.

        Args:
            sections (Optional[List[InfoSection]]): A list of InfoSection values specifying which sections of
            information to retrieve. When no parameter is provided, the default option is assumed.
            route (Optional[Route]): The command will be routed automatically, unless `route` is provided, in which
            case the client will initially try to route the command to the nodes defined by `route`. Defaults to None.

        Returns:
            Union[List[List[str]], str]: If a single node route is requested, returns a string containing the information for
            the required sections. Otherwise, returns a list of lists of strings, with each sub-list containing the address of
            the queried node and the information regarding the requested sections.
        """
        args = [section.value for section in sections] if sections else []
        return cast(
            Union[List[List[str]], str],
            await self._execute_command(RequestType.Info, args, route),
        )

    async def exec(
        self,
        transaction: BaseTransaction | ClusterTransaction,
        route: Optional[Route] = None,
    ) -> List[TResult]:
        """Execute a transaction by processing the queued commands.
        See https://redis.io/topics/Transactions/ for details on Redis Transactions.

        Args:
            transaction (ClusterTransaction): A ClusterTransaction object containing a list of commands to be executed.
            route (Optional[Route]): The command will be routed automatically, unless `route` is provided, in which
            case the client will initially try to route the command to the nodes defined by `route`. Defaults to None.

        Returns:
            List[TResult]: A list of results corresponding to the execution of each command
            in the transaction. If a command returns a value, it will be included in the list. If a command
            doesn't return a value, the list entry will be None.
        """
        commands = transaction.commands[:]
        return await self.execute_transaction(commands, route)

    async def config_resetstat(
        self,
        route: Optional[Route] = None,
    ) -> TOK:
        """Reset the statistics reported by Redis.
        See https://redis.io/commands/config-resetstat/ for details.
        Args:
            route (Optional[Route]): The command will be routed automatically, unless `route` is provided, in which
            case the client will initially try to route the command to the nodes defined by `route`. Defaults to None.
        Returns:
            OK: Returns "OK" to confirm that the statistics were successfully reset.
        """
        return cast(
            TOK, await self._execute_command(RequestType.ConfigResetStat, [], route)
        )

    async def config_rewrite(
        self,
        route: Optional[Route] = None,
    ) -> TOK:
        """Rewrite the configuration file with the current configuration.
        See https://redis.io/commands/config-rewrite/ for details.
        Args:
            route (Optional[TRoute]): The command will be routed automatically, unless `route` is provided, in which
            case the client will initially try to route the command to the nodes defined by `route`. Defaults to None.
        Returns:
            OK: OK is returned when the configuration was rewritten properly. Otherwise an error is returned.
        """
        return cast(
            TOK, await self._execute_command(RequestType.ConfigRewrite, [], route)
        )
