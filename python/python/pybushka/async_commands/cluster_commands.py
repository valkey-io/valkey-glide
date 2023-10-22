from __future__ import annotations

from typing import Dict, List, Mapping, Optional, TypeVar, Union, cast

from pybushka.async_commands.core import CoreCommands, InfoSection
from pybushka.async_commands.transaction import BaseTransaction, ClusterTransaction
from pybushka.constants import TOK, TClusterResponse, TResult
from pybushka.protobuf.redis_request_pb2 import RequestType
from pybushka.routes import Route

T = TypeVar("T")

# TODO: remove constant parameters after redis-rs return type for multi node is Dict[str , T]
# This constant value is used to define the structure of a cluster single response type
# without the necessity of recreating it for each use, thereby conserving memory
LIST_STR = [""]


def is_single_response(response: T, single_res: T) -> bool:
    """
    Recursively checks if a given response matches the type structure of single_res.

    Args:
        response (T): The response to check.
        single_res (T): An object with the expected type structure as an example for the single node response.

    Returns:
        bool: True if response matches the structure of single_res, False otherwise.

     Example:
        >>> is_single_response(["value"], LIST_STR)
        True
        >>> is_single_response([["value"]], LIST_STR)
        False
    """
    if isinstance(single_res, list) and isinstance(response, list):
        return is_single_response(response[0], single_res[0])
    elif isinstance(response, type(single_res)):
        return True
    return False


def convert_multi_node_res_to_dict(
    response: List[List[Union[str, T]]],
) -> Dict[str, T]:
    """
    Convert the multi-node response from a list of [address, nodeResponse] pairs to a dictionary {address: nodeResponse}.

    Args:
        response (List[List[Union[str, T]]]): A list of lists, where each inner list contains an address (str)
            and the corresponding node response (of type T).

    Returns:
        Dict[str, T]: A dictionary where each address is the key and its corresponding node response is the value.

    Example:
        >>> response = [["node1", "value1"], ["node2", "value2"]]
        >>> convert_multi_node_res_to_dict(response)
        {'node1': 'value1', 'node2': 'value2'}
    """
    dict_res: Dict[str, T] = {}
    while len(response) > 0:
        cur_res = response.pop()
        if cur_res is not None and isinstance(cur_res[0], str):
            dict_res[cur_res[0]] = cast(T, cur_res[1])

    return dict_res


class ClusterCommands(CoreCommands):
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
    ) -> TClusterResponse[str]:
        """Get information and statistics about the Redis server.
        See https://redis.io/commands/info/ for details.

        Args:
            sections (Optional[List[InfoSection]]): A list of InfoSection values specifying which sections of
            information to retrieve. When no parameter is provided, the default option is assumed.
            route (Optional[Route]): The command will be routed automatically, unless `route` is provided, in which
            case the client will initially try to route the command to the nodes defined by `route`. Defaults to None.

        Returns:
            TClusterResponse[str]: If a single node route is requested, returns a string containing the information for
            the required sections. Otherwise, returns a dict of strings, with each key containing the address of
            the queried node and value containing the information regarding the requested sections.
        """
        args = [section.value for section in sections] if sections else []

        info_res = cast(
            Union[str, List[List[str]]],
            await self._execute_command(RequestType.Info, args, route),
        )
        return (
            info_res
            if isinstance(info_res, str)
            else convert_multi_node_res_to_dict(info_res)
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

    async def client_id(
        self,
        route: Optional[Route] = None,
    ) -> TClusterResponse[int]:
        """Returns the current connection id.
        See https://redis.io/commands/client-id/ for more information.
        Args:
            route (Optional[Route]): The command will be sent to a random node, unless `route` is provided, in which
            case the client will route the command to the nodes defined by `route`.

        Returns:
            TClusterResponse[int]: The id of the client.
            If a single node route is requested, returns a int representing the client's id.
            Otherwise, returns a dict of [str , int] where each key contains the address of
            the queried node and the value contains the client's id.
        """
        client_id_res = cast(
            Union[int, List[List[Union[str, int]]]],
            await self._execute_command(RequestType.ClientId, [], route),
        )
        return (
            client_id_res
            if isinstance(client_id_res, int)
            else convert_multi_node_res_to_dict(client_id_res)
        )

    async def ping(
        self, message: Optional[str] = None, route: Optional[Route] = None
    ) -> str:
        """Ping the Redis server.
        See https://redis.io/commands/ping/ for more details.
        Args:
           message (Optional[str]): An optional message to include in the PING command. If not provided,
            the server will respond with "PONG". If provided, the server will respond with a copy of the message.

            route (Optional[Route]): : The command will be sent to all primaries, unless `route` is provided, in which
            case the client will route the command to the nodes defined by `route`

        Returns:
           str: "PONG" if 'message' is not provided, otherwise return a copy of 'message'.

        Examples:
            >>> ping()
            "PONG"
            >>> ping("Hello")
            "Hello"
        """
        argument = [] if message is None else [message]
        return cast(str, await self._execute_command(RequestType.Ping, argument, route))

    async def config_get(
        self, parameters: List[str], route: Optional[Route] = None
    ) -> TClusterResponse[List[str]]:
        """Get the values of configuration parameters.
        See https://redis.io/commands/config-get/ for details.

        Args:
            parameters (List[str]): A list of configuration parameter names to retrieve values for.

            route (Optional[Route]): The command will be routed to all nodes, unless route is provided,
            in which case the client will route the command to the nodes defined by route.

        Returns:
            TClusterResponse[List[str]]: A list of values corresponding to the
            configuration parameters.
            When specifying a route other than a single node, response will be : {Address (str) : response (List[str]) , ... }
            with type of Dict[str, List[str]].

        Examples:
            >>> config_get(["timeout"] , RandomNode())
            ['timeout', '1000']
            >>> config_get(["timeout" , "maxmemory"])
            ['timeout', '1000', "maxmemory", "1GB"]
        """
        config_get_res = await self._execute_command(
            RequestType.ConfigGet, parameters, route
        )

        return (
            cast(List[str], config_get_res)
            if is_single_response(config_get_res, LIST_STR)
            else convert_multi_node_res_to_dict(
                cast(List[List[Union[str, List[str]]]], config_get_res)
            )
        )

    async def config_set(
        self, parameters_map: Mapping[str, str], route: Optional[Route] = None
    ) -> TOK:
        """Set configuration parameters to the specified values.
        See https://redis.io/commands/config-set/ for details.

        Args:
            parameters_map (Mapping[str, str]): A map consisting of configuration
            parameters and their respective values to set.

            route (Optional[Route]): The command will be routed to all nodes, unless route is provided,
            in which case the client will route the command to the nodes defined by route.

        Returns:
            OK: Returns OK if all configurations have been successfully set. Otherwise, raises an error.

        Examples:
            >>> config_set([("timeout", "1000")], [("maxmemory", "1GB")])
            OK
        """
        parameters: List[str] = []
        for pair in parameters_map.items():
            parameters.extend(pair)
        return cast(
            TOK,
            await self._execute_command(RequestType.ConfigSet, parameters, route),
        )

    async def client_getname(
        self, route: Optional[Route] = None
    ) -> TClusterResponse[Optional[str]]:
        """
        Get the name of the connection to which the request is routed.
        See https://redis.io/commands/client-getname/ for more details.
        Args:
            route (Optional[Route]): The command will be routed to a random node, unless route is provided,
            in which case the client will route the command to the nodes defined by route.

        Returns:
            TClusterResponse[Optional[str]]: The name of the client connection as a string if a name is set,
            or None if no name is assigned.
            When specifying a route other than a single node, response will be:
            {Address (str) : response (Optional[str]) , ... } with type of Dict[str, Optional[str]].

        Examples:
            >>> client_getname()
            'Connection Name'
            >>> client_getname(AllNodes())
            {'addr': 'Connection Name'', 'addr2': 'Connection Name', 'addr3': 'Connection Name'}
        """

        client_get_name = await self._execute_command(
            RequestType.ClientGetName, [], route
        )

        return (
            cast(Optional[str], client_get_name)
            if isinstance(client_get_name, str) or client_get_name is None
            else convert_multi_node_res_to_dict(
                cast(List[List[Union[str, Optional[str]]]], client_get_name)
            )
        )
