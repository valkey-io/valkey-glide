# Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

from __future__ import annotations

from typing import Dict, List, Mapping, Optional, cast

from glide.commands.core_options import FlushMode, FunctionRestorePolicy, InfoSection
from glide.commands.sync_commands.core import CoreCommands
from glide.constants import (
    TOK,
    TClusterResponse,
    TEncodable,
    TFunctionListResponse,
    TFunctionStatsSingleNodeResponse,
    TResult,
)
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

    def config_resetstat(
        self,
        route: Optional[Route] = None,
    ) -> TOK:
        """
        Resets the statistics reported by the server using the INFO and LATENCY HISTOGRAM commands.

        See [valkey.io](https://valkey.io/commands/config-resetstat/) for details.

        Args:
            route (Optional[Route]): The command will be routed automatically to all nodes, unless `route` is provided, in
                which case the client will route the command to the nodes defined by `route`. Defaults to None.

        Returns:
            OK: Returns "OK" to confirm that the statistics were successfully reset.
        """
        return cast(TOK, self._execute_command(RequestType.ConfigResetStat, [], route))

    def config_rewrite(
        self,
        route: Optional[Route] = None,
    ) -> TOK:
        """
        Rewrite the configuration file with the current configuration.

        See [valkey.io](https://valkey.io/commands/config-rewrite/) for details.

        Args:
            route (Optional[TRoute]): The command will be routed automatically to all nodes, unless `route` is provided, in
                which case the client will route the command to the nodes defined by `route`. Defaults to None.

        Returns:
            OK: OK is returned when the configuration was rewritten properly. Otherwise an error is raised.

        Example:
            >>> client.config_rewrite()
                'OK'
        """
        return cast(TOK, self._execute_command(RequestType.ConfigRewrite, [], route))

    def client_id(
        self,
        route: Optional[Route] = None,
    ) -> TClusterResponse[int]:
        """
        Returns the current connection id.

        See [valkey.io](https://valkey.io/commands/client-id/) for more information.

        Args:
            route (Optional[Route]): The command will be sent to a random node, unless `route` is provided, in which
                case the client will route the command to the nodes defined by `route`.

        Returns:
            TClusterResponse[int]: The id of the client.

            If a single node route is requested, returns a int representing the client's id.

            Otherwise, returns a dict of [byte , int] where each key contains the address of
            the queried node and the value contains the client's id.
        """
        return cast(
            TClusterResponse[int],
            self._execute_command(RequestType.ClientId, [], route),
        )

    def ping(
        self, message: Optional[TEncodable] = None, route: Optional[Route] = None
    ) -> bytes:
        """
        Ping the server.

        See [valkey.io](https://valkey.io/commands/ping/) for more details.

        Args:
            message (Optional[TEncodable]): An optional message to include in the PING command. If not provided,
                the server will respond with b"PONG". If provided, the server will respond with a copy of the message.
            route (Optional[Route]): The command will be sent to all primaries, unless `route` is provided, in which
                case the client will route the command to the nodes defined by `route`

        Returns:
           bytes: b'PONG' if `message` is not provided, otherwise return a copy of `message`.

        Examples:
            >>> client.ping()
                b"PONG"
            >>> client.ping("Hello")
                b"Hello"
        """
        argument = [] if message is None else [message]
        return cast(bytes, self._execute_command(RequestType.Ping, argument, route))

    def config_get(
        self, parameters: List[TEncodable], route: Optional[Route] = None
    ) -> TClusterResponse[Dict[bytes, bytes]]:
        """
        Get the values of configuration parameters.
        Starting from server version 7, command supports multiple parameters.

        See [valkey.io](https://valkey.io/commands/config-get/) for details.

        Args:
            parameters (List[TEncodable]): A list of configuration parameter names to retrieve values for.
            route (Optional[Route]): The command will be routed to a random node, unless `route` is provided,
                in which case the client will route the command to the nodes defined by `route`.

        Returns:
            TClusterResponse[Dict[bytes, bytes]]: A dictionary of values corresponding to the
            configuration parameters.
            When specifying a route other than a single node, response will be::

                {Address (bytes) : response (Dict[bytes, bytes]) , ... }

            with type of Dict[bytes, Dict[bytes, bytes]].

        Examples:
            >>> client.config_get(["timeout"] , RandomNode())
                {b'timeout': b'1000'}
            >>> client.config_get(["timeout" , b"maxmemory"])
                {b'timeout': b'1000', b"maxmemory": b"1GB"}
        """
        return cast(
            TClusterResponse[Dict[bytes, bytes]],
            self._execute_command(RequestType.ConfigGet, parameters, route),
        )

    def config_set(
        self,
        parameters_map: Mapping[TEncodable, TEncodable],
        route: Optional[Route] = None,
    ) -> TOK:
        """
        Set configuration parameters to the specified values.
        Starting from server version 7, command supports multiple parameters.

        See [valkey.io](https://valkey.io/commands/config-set/) for details.

        Args:
            parameters_map (Mapping[TEncodable, TEncodable]): A map consisting of configuration
                parameters and their respective values to set.
            route (Optional[Route]): The command will be routed to all nodes, unless `route` is provided,
                in which case the client will route the command to the nodes defined by `route`.

        Returns:
            OK: Returns OK if all configurations have been successfully set. Otherwise, raises an error.

        Examples:
            >>> client.config_set({"timeout": "1000", b"maxmemory": b"1GB"})
                OK
        """
        parameters: List[TEncodable] = []
        for pair in parameters_map.items():
            parameters.extend(pair)
        return cast(
            TOK,
            self._execute_command(RequestType.ConfigSet, parameters, route),
        )

    def client_getname(
        self, route: Optional[Route] = None
    ) -> TClusterResponse[Optional[bytes]]:
        """
        Get the name of the connection to which the request is routed.

        See [valkey.io](https://valkey.io/commands/client-getname/) for more details.

        Args:
            route (Optional[Route]): The command will be routed to a random node, unless `route` is provided,
                in which case the client will route the command to the nodes defined by `route`.

        Returns:
            TClusterResponse[Optional[bytes]]: The name of the client connection as a bytes string if a name is set,
            or None if no name is assigned.

            When specifying a route other than a single node, response will be::

                {Address (bytes) : response (Optional[bytes]) , ... }

            with type of Dict[str, Optional[str]].

        Examples:
            >>> client.client_getname()
                b'Connection Name'
            >>> client.client_getname(AllNodes())
                {b'addr': b'Connection Name', b'addr2': b'Connection Name', b'addr3': b'Connection Name'}
        """
        return cast(
            TClusterResponse[Optional[bytes]],
            self._execute_command(RequestType.ClientGetName, [], route),
        )

    def dbsize(self, route: Optional[Route] = None) -> int:
        """
        Returns the number of keys in the database.

        See [valkey.io](https://valkey.io/commands/dbsize) for more details.

        Args:
            route (Optional[Route]): The command will be routed to all primaries, unless `route` is provided,
                in which case the client will route the command to the nodes defined by `route`.

        Returns:
            int: The number of keys in the database.

            In the case of routing the query to multiple nodes, returns the aggregated number of keys across the
            different nodes.

        Examples:
            >>> client.dbsize()
                10  # Indicates there are 10 keys in the cluster.
        """
        return cast(int, self._execute_command(RequestType.DBSize, [], route))

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

    def function_load(
        self,
        library_code: TEncodable,
        replace: bool = False,
        route: Optional[Route] = None,
    ) -> bytes:
        """
        Loads a library to Valkey.

        See [valkey.io](https://valkey.io/commands/function-load/) for more details.

        Args:
            library_code (TEncodable): The source code that implements the library.
            replace (bool): Whether the given library should overwrite a library with the same name if
                it already exists.
            route (Optional[Route]): The command will be routed to all primaries, unless `route` is provided,
                in which case the client will route the command to the nodes defined by `route`.

        Returns:
            bytes: The library name that was loaded.

        Examples:
            >>> code = "#!lua name=mylib \\n redis.register_function('myfunc', function(keys, args) return args[1] end)"
            >>> client.function_load(code, True, RandomNode())
                b"mylib"

        Since: Valkey 7.0.0.
        """
        return cast(
            bytes,
            self._execute_command(
                RequestType.FunctionLoad,
                ["REPLACE", library_code] if replace else [library_code],
                route,
            ),
        )

    def function_list(
        self,
        library_name_pattern: Optional[TEncodable] = None,
        with_code: bool = False,
        route: Optional[Route] = None,
    ) -> TClusterResponse[TFunctionListResponse]:
        """
        Returns information about the functions and libraries.

        See [valkey.io](https://valkey.io/commands/function-list/) for more details.

        Args:
            library_name_pattern (Optional[TEncodable]):  A wildcard pattern for matching library names.
            with_code (bool): Specifies whether to request the library code from the server or not.
            route (Optional[Route]): The command will be routed to a random node, unless `route` is provided,
                in which case the client will route the command to the nodes defined by `route`.

        Returns:
            TClusterResponse[TFunctionListResponse]: Info
            about all or selected libraries and their functions.

        Examples:
            >>> response = client.function_list("myLib?_backup", True)
                [{
                    b"library_name": b"myLib5_backup",
                    b"engine": b"LUA",
                    b"functions": [{
                        b"name": b"myfunc",
                        b"description": None,
                        b"flags": {b"no-writes"},
                    }],
                    b"library_code":
                        b"#!lua name=mylib \\n redis.register_function('myfunc', function(keys, args) " \\
                        b"return args[1] end)"
                }]

        Since: Valkey 7.0.0.
        """
        args = []
        if library_name_pattern is not None:
            args.extend(["LIBRARYNAME", library_name_pattern])
        if with_code:
            args.append("WITHCODE")
        return cast(
            TClusterResponse[TFunctionListResponse],
            self._execute_command(
                RequestType.FunctionList,
                args,
                route,
            ),
        )

    def function_flush(
        self, mode: Optional[FlushMode] = None, route: Optional[Route] = None
    ) -> TOK:
        """
        Deletes all function libraries.

        See [valkey.io](https://valkey.io/commands/function-flush/) for more details.

        Args:
            mode (Optional[FlushMode]): The flushing mode, could be either `SYNC` or `ASYNC`.
            route (Optional[Route]): The command will be routed to all primaries, unless `route` is provided,
                in which case the client will route the command to the nodes defined by `route`.

        Returns:
            TOK: A simple `OK`.

        Examples:
            >>> client.function_flush(FlushMode.SYNC)
                "OK"

        Since: Valkey 7.0.0.
        """
        return cast(
            TOK,
            self._execute_command(
                RequestType.FunctionFlush,
                [mode.value] if mode else [],
                route,
            ),
        )

    def function_delete(
        self, library_name: TEncodable, route: Optional[Route] = None
    ) -> TOK:
        """
        Deletes a library and all its functions.

        See [valkey.io](https://valkey.io/commands/function-delete/) for more details.

        Args:
            library_name (TEncodable): The library name to delete
            route (Optional[Route]): The command will be routed to all primaries, unless `route` is provided,
                in which case the client will route the command to the nodes defined by `route`.

        Returns:
            TOK: A simple `OK`.

        Examples:
            >>> client.function_delete("my_lib")
                "OK"

        Since: Valkey 7.0.0.
        """
        return cast(
            TOK,
            self._execute_command(
                RequestType.FunctionDelete,
                [library_name],
                route,
            ),
        )

    def function_kill(self, route: Optional[Route] = None) -> TOK:
        """
        Kills a function that is currently executing.
        This command only terminates read-only functions.

        See [valkey.io](https://valkey.io/commands/function-kill/) for more details.

        Args:
            route (Optional[Route]): The command will be routed to all nodes, unless `route` is provided,
                in which case the client will route the command to the nodes defined by `route`.

        Returns:
            TOK: A simple `OK`.

        Examples:
            >>> client.function_kill()
                "OK"

        Since: Valkey 7.0.0.
        """
        return cast(
            TOK,
            self._execute_command(
                RequestType.FunctionKill,
                [],
                route,
            ),
        )

    def fcall_route(
        self,
        function: TEncodable,
        arguments: Optional[List[TEncodable]] = None,
        route: Optional[Route] = None,
    ) -> TClusterResponse[TResult]:
        """
        Invokes a previously loaded function.

        See [valkey.io](https://valkey.io/commands/fcall/) for more details.

        Args:
            function (TEncodable): The function name.
            arguments (Optional[List[TEncodable]]): A list of `function` arguments. `Arguments`
                should not represent names of keys.
            route (Optional[Route]): The command will be routed to a random primary node, unless `route` is provided, in which
                case the client will route the command to the nodes defined by `route`. Defaults to None.

        Returns:
            TClusterResponse[TResult]: If a single node route is requested,
            returns a Optional[TResult] representing the function's return value.

            Otherwise, returns a dict of [bytes , Optional[TResult]] where each key contains the address of
            the queried node and the value contains the function's return value.

        Example:
            >>> client.fcall(
            ...     "Deep_Thought",
            ...     ["Answer", "to", "the", "Ultimate", "Question", "of", "Life,", "the", "Universe,", "and", "Everything"],
            ...     RandomNode()
            ... )
                b'new_value' # Returns the function's return value.

        Since: Valkey version 7.0.0.
        """
        args = [function, "0"]
        if arguments is not None:
            args.extend(arguments)
        return cast(
            TClusterResponse[TResult],
            self._execute_command(RequestType.FCall, args, route),
        )

    def fcall_ro_route(
        self,
        function: TEncodable,
        arguments: Optional[List[TEncodable]] = None,
        route: Optional[Route] = None,
    ) -> TClusterResponse[TResult]:
        """
        Invokes a previously loaded read-only function.

        See [valkey.io](https://valkey.io/commands/fcall_ro) for more details.

        Args:
            function (TEncodable): The function name.
            arguments (List[TEncodable]): An `array` of `function` arguments. `arguments` should not
                represent names of keys.
            route (Optional[Route]): Specifies the routing configuration of the command. The client
                will route the command to the nodes defined by `route`.

        Returns:
            TClusterResponse[TResult]: The return value depends on the function that was executed.

        Examples:
            >>> client.fcall_ro_route("Deep_Thought", ALL_NODES)
                42 # The return value on the function that was executed

        Since: Valkey version 7.0.0.
        """
        args: List[TEncodable] = [function, "0"]
        if arguments is not None:
            args.extend(arguments)
        return cast(
            TClusterResponse[TResult],
            self._execute_command(RequestType.FCallReadOnly, args, route),
        )

    def function_stats(
        self, route: Optional[Route] = None
    ) -> TClusterResponse[TFunctionStatsSingleNodeResponse]:
        """
        Returns information about the function that's currently running and information about the
        available execution engines.

        See [valkey.io](https://valkey.io/commands/function-stats/) for more details

        Args:
            route (Optional[Route]): The command will be routed automatically to all nodes, unless `route` is provided, in
                which case the client will route the command to the nodes defined by `route`. Defaults to None.

        Returns:
            TClusterResponse[TFunctionStatsSingleNodeResponse]: A `Mapping` with two keys:

                - `running_script` with information about the running script.
                - `engines` with information about available engines and their stats.

            See example for more details.

        Examples:
            >>> client.function_stats(RandomNode())
                {
                    'running_script': {
                        'name': 'foo',
                        'command': ['FCALL', 'foo', '0', 'hello'],
                        'duration_ms': 7758
                    },
                    'engines': {
                        'LUA': {
                            'libraries_count': 1,
                            'functions_count': 1,
                        }
                    }
                }

        Since: Valkey version 7.0.0.
        """
        return cast(
            TClusterResponse[TFunctionStatsSingleNodeResponse],
            self._execute_command(RequestType.FunctionStats, [], route),
        )

    def function_dump(self, route: Optional[Route] = None) -> TClusterResponse[bytes]:
        """
        Returns the serialized payload of all loaded libraries.

        See [valkey.io](https://valkey.io/commands/function-dump/) for more details.

        Args:
            route (Optional[Route]): The command will be routed to a random node, unless
                `route` is provided, in which case the client will route the command to the
                nodes defined by `route`.

        Returns:
            TClusterResponse[bytes]: The serialized payload of all loaded libraries.

        Examples:
            >>> payload = client.function_dump()
                # The serialized payload of all loaded libraries. This response can
                # be used to restore loaded functions on any Valkey instance.
            >>> client.function_restore(payload)
                "OK" # The serialized dump response was used to restore the libraries.

        Since: Valkey 7.0.0.
        """
        return cast(
            TClusterResponse[bytes],
            self._execute_command(RequestType.FunctionDump, [], route),
        )

    def function_restore(
        self,
        payload: TEncodable,
        policy: Optional[FunctionRestorePolicy] = None,
        route: Optional[Route] = None,
    ) -> TOK:
        """
        Restores libraries from the serialized payload returned by the `function_dump` command.

        See [valkey.io](https://valkey.io/commands/function-restore/) for more details.

        Args:
            payload (bytes): The serialized data from the `function_dump` command.
            policy (Optional[FunctionRestorePolicy]): A policy for handling existing libraries.
            route (Optional[Route]): The command will be sent to all primaries, unless
                `route` is provided, in which case the client will route the command to the
                nodes defined by `route`.

        Returns:
            TOK: OK.

        Examples:
            >>> payload = client.function_dump()
                # The serialized payload of all loaded libraries. This response can
                # be used to restore loaded functions on any Valkey instance.
            >>> client.function_restore(payload, AllPrimaries())
                "OK" # The serialized dump response was used to restore the libraries with the specified route.
            >>> client.function_restore(payload, FunctionRestorePolicy.FLUSH, AllPrimaries())
                "OK" # The serialized dump response was used to restore the libraries with the specified route and policy.

        Since: Valkey 7.0.0.
        """
        args: List[TEncodable] = [payload]
        if policy is not None:
            args.append(policy.value)

        return cast(
            TOK, self._execute_command(RequestType.FunctionRestore, args, route)
        )

    def time(self, route: Optional[Route] = None) -> TClusterResponse[List[bytes]]:
        """
        Returns the server time.

        See [valkey.io](https://valkey.io/commands/time/) for more details.

        Args:
            route (Optional[Route]): The command will be routed to a random node, unless `route` is provided,
                in which case the client will route the command to the nodes defined by `route`.

        Returns:
            TClusterResponse[Optional[bytes]]:  The current server time as a two items `array`:
            A Unix timestamp and the amount of microseconds already elapsed in the current second.
            The returned `array` is in a [Unix timestamp, Microseconds already elapsed] format.

            When specifying a route other than a single node, response will be::

                {Address (bytes) : response (List[bytes]) , ... }

            with type of Dict[bytes, List[bytes]].

        Examples:
            >>> client.time()
                [b'1710925775', b'913580']
            >>> client.time(AllNodes())
                {
                    b'addr': [b'1710925775', b'913580'],
                    b'addr2': [b'1710925775', b'913580'],
                    b'addr3': [b'1710925775', b'913580']
                }
        """
        return cast(
            TClusterResponse[List[bytes]],
            self._execute_command(RequestType.Time, [], route),
        )

    def lastsave(self, route: Optional[Route] = None) -> TClusterResponse[int]:
        """
        Returns the Unix time of the last DB save timestamp or startup timestamp if no save was made since then.

        See [valkey.io](https://valkey.io/commands/lastsave) for more details.

        Args:
            route (Optional[Route]): The command will be routed to a random node, unless `route` is provided,
                in which case the client will route the command to the nodes defined by `route`.

        Returns:
            TClusterResponse[int]: The Unix time of the last successful DB save.

            If no route is provided, or a single node route is requested, returns an int representing the Unix time
            of the last successful DB save.

            Otherwise, returns a dict of [bytes , int] where each key contains the
            address of the queried node and the value contains the Unix time of the last successful DB save.

        Examples:
            >>> client.lastsave()
                1710925775  # Unix time of the last DB save
            >>> client.lastsave(AllNodes())
                {b'addr1': 1710925775, b'addr2': 1710925775, b'addr3': 1710925775}  # Unix time of the last DB save on
                                                                                    # each node
        """
        return cast(
            TClusterResponse[int],
            self._execute_command(RequestType.LastSave, [], route),
        )

    def flushall(
        self, flush_mode: Optional[FlushMode] = None, route: Optional[Route] = None
    ) -> TOK:
        """
        Deletes all the keys of all the existing databases. This command never fails.

        See [valkey.io](https://valkey.io/commands/flushall) for more details.

        Args:
            flush_mode (Optional[FlushMode]): The flushing mode, could be either `SYNC` or `ASYNC`.
            route (Optional[Route]): The command will be routed to all primary nodes, unless `route` is provided,
                in which case the client will route the command to the nodes defined by `route`.

        Returns:
            TOK: A simple OK response.

        Examples:
            >>> client.flushall(FlushMode.ASYNC)
                OK  # This command never fails.
            >>> client.flushall(FlushMode.ASYNC, AllNodes())
                OK  # This command never fails.
        """
        args: List[TEncodable] = []
        if flush_mode is not None:
            args.append(flush_mode.value)

        return cast(
            TOK,
            self._execute_command(RequestType.FlushAll, args, route),
        )

    def flushdb(
        self, flush_mode: Optional[FlushMode] = None, route: Optional[Route] = None
    ) -> TOK:
        """
        Deletes all the keys of the currently selected database. This command never fails.

        See [valkey.io](https://valkey.io/commands/flushdb) for more details.

        Args:
            flush_mode (Optional[FlushMode]): The flushing mode, could be either `SYNC` or `ASYNC`.
            route (Optional[Route]): The command will be routed to all primary nodes, unless `route` is provided,
                in which case the client will route the command to the nodes defined by `route`.

        Returns:
            TOK: A simple OK response.

        Examples:
            >>> client.flushdb()
                OK  # The keys of the currently selected database were deleted.
            >>> client.flushdb(FlushMode.ASYNC)
                OK  # The keys of the currently selected database were deleted asynchronously.
            >>> client.flushdb(FlushMode.ASYNC, AllNodes())
                OK  # The keys of the currently selected database were deleted asynchronously on all nodes.
        """
        args: List[TEncodable] = []
        if flush_mode is not None:
            args.append(flush_mode.value)

        return cast(
            TOK,
            self._execute_command(RequestType.FlushDB, args, route),
        )

    def copy(
        self,
        source: TEncodable,
        destination: TEncodable,
        replace: Optional[bool] = None,
    ) -> bool:
        """
        Copies the value stored at the `source` to the `destination` key. When `replace` is True,
        removes the `destination` key first if it already exists, otherwise performs no action.

        See [valkey.io](https://valkey.io/commands/copy) for more details.

        Note:
            Both `source` and `destination` must map to the same hash slot.

        Args:
            source (TEncodable): The key to the source value.
            destination (TEncodable): The key where the value should be copied to.
            replace (Optional[bool]): If the destination key should be removed before copying the value to it.

        Returns:
            bool: True if the source was copied. Otherwise, returns False.

        Examples:
            >>> client.set("source", "sheep")
            >>> client.copy(b"source", b"destination")
                True # Source was copied
            >>> client.get("destination")
                b"sheep"

        Since: Valkey version 6.2.0.
        """
        args: List[TEncodable] = [source, destination]
        if replace is True:
            args.append("REPLACE")
        return cast(
            bool,
            self._execute_command(RequestType.Copy, args),
        )

    def lolwut(
        self,
        version: Optional[int] = None,
        parameters: Optional[List[int]] = None,
        route: Optional[Route] = None,
    ) -> TClusterResponse[bytes]:
        """
        Displays a piece of generative computer art and the Valkey version.

        See [valkey.io](https://valkey.io/commands/lolwut) for more details.

        Args:
            version (Optional[int]): Version of computer art to generate.
            parameters (Optional[List[int]]): Additional set of arguments in order to change the output:

                - For version `5`, those are length of the line, number of squares per row, and number of squares per column.
                - For version `6`, those are number of columns and number of lines.

            route (Optional[Route]): The command will be routed to a random node, unless `route` is provided,
                in which case the client will route the command to the nodes defined by `route`.

        Returns:
            TClusterResponse[bytes]: A piece of generative computer art along with the current Valkey version.

            When specifying a route other than a single node, response will be::

                {Address (bytes) : response (bytes) , ... }

            with type of Dict[bytes, bytes].

        Examples:
            >>> client.lolwut(6, [40, 20], RandomNode());
                b"Redis ver. 7.2.3" # Indicates the current Valkey version
        """
        args: List[TEncodable] = []
        if version is not None:
            args.extend(["VERSION", str(version)])
        if parameters:
            for var in parameters:
                args.extend(str(var))
        return cast(
            TClusterResponse[bytes],
            self._execute_command(RequestType.Lolwut, args, route),
        )

    def random_key(self, route: Optional[Route] = None) -> Optional[bytes]:
        """
        Returns a random existing key name.

        See [valkey.io](https://valkey.io/commands/randomkey) for more details.

        Args:
            route (Optional[Route]): The command will be routed to all primary nodes, unless `route` is provided,
                in which case the client will route the command to the nodes defined by `route`.

        Returns:
            Optional[bytes]: A random existing key name.

        Examples:
            >>> client.random_key()
                b"random_key_name"  # "random_key_name" is a random existing key name.
        """
        return cast(
            Optional[bytes],
            self._execute_command(RequestType.RandomKey, [], route),
        )

    def wait(
        self,
        numreplicas: int,
        timeout: int,
        route: Optional[Route] = None,
    ) -> int:
        """
        Blocks the current client until all the previous write commands are successfully transferred
        and acknowledged by at least `numreplicas` of replicas. If `timeout` is
        reached, the command returns even if the specified number of replicas were not yet reached.

        See [valkey.io](https://valkey.io/commands/wait) for more details.

        Args:
            numreplicas (int): The number of replicas to reach.
            timeout (int): The timeout value specified in milliseconds. A value of 0 will block indefinitely.
            route (Optional[Route]): The command will be routed to all primary nodes, unless `route` is provided,
                in which case the client will route the command to the nodes defined by `route`.

        Returns:
            int: The number of replicas reached by all the writes performed in the context of the current connection.

        Examples:
            >>> client.set("key", "value");
            >>> client.wait(1, 1000);
            # return 1 when a replica is reached or 0 if 1000ms is reached.
        """
        args: List[TEncodable] = [str(numreplicas), str(timeout)]
        return cast(
            int,
            self._execute_command(RequestType.Wait, args, route),
        )
