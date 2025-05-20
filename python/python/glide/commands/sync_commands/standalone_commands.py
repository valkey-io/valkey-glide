# Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

from __future__ import annotations

from typing import Dict, List, Mapping, Optional, cast

from glide.commands.core_options import FlushMode, FunctionRestorePolicy, InfoSection
from glide.commands.sync_commands.core import CoreCommands
from glide.constants import (
    TOK,
    TEncodable,
    TFunctionListResponse,
    TFunctionStatsFullResponse,
    TResult,
)
from glide.protobuf.command_request_pb2 import RequestType


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

    def select(self, index: int) -> TOK:
        """
        Change the currently selected database.

        See [valkey.io](https://valkey.io/commands/select/) for details.

        Args:
            index (int): The index of the database to select.

        Returns:
            A simple OK response.
        """
        return cast(TOK, self._execute_command(RequestType.Select, [str(index)]))

    def config_resetstat(self) -> TOK:
        """
        Resets the statistics reported by the server using the INFO and LATENCY HISTOGRAM commands.

        See [valkey.io](https://valkey.io/commands/config-resetstat/) for details.

        Returns:
            OK: Returns "OK" to confirm that the statistics were successfully reset.
        """
        return cast(TOK, self._execute_command(RequestType.ConfigResetStat, []))

    def config_rewrite(self) -> TOK:
        """
        Rewrite the configuration file with the current configuration.

        See [valkey.io](https://valkey.io/commands/config-rewrite/) for details.

        Returns:
            OK: OK is returned when the configuration was rewritten properly.

            Otherwise, an error is raised.
        """
        return cast(TOK, self._execute_command(RequestType.ConfigRewrite, []))

    def client_id(
        self,
    ) -> int:
        """
        Returns the current connection id.

        See [valkey.io](https://valkey.io/commands/client-id/) for more information.

        Returns:
            int: the id of the client.
        """
        return cast(int, self._execute_command(RequestType.ClientId, []))

    def ping(self, message: Optional[TEncodable] = None) -> bytes:
        """
        Ping the server.

        See [valkey.io](https://valkey.io/commands/ping/) for more details.

        Args:
            message (Optional[TEncodable]): An optional message to include in the PING command. If not provided,
                the server will respond with b"PONG". If provided, the server will respond with a copy of the message.

        Returns:
            bytes: b"PONG" if `message` is not provided.

            Otherwise return a copy of `message`.

        Examples:
            >>> client.ping()
                b"PONG"
            >>> client.ping("Hello")
                b"Hello"
        """
        argument = [] if message is None else [message]
        return cast(bytes, self._execute_command(RequestType.Ping, argument))

    def config_get(self, parameters: List[TEncodable]) -> Dict[bytes, bytes]:
        """
        Get the values of configuration parameters.
        Starting from server version 7, command supports multiple parameters

        See [valkey.io](https://valkey.io/commands/config-get/) for details.

        Args:
            parameters (List[TEncodable]): A list of configuration parameter names to retrieve values for.

        Returns:
            Dict[bytes, bytes]: A dictionary of values corresponding to the configuration parameters.

        Examples:
            >>> client.config_get(["timeout"] , RandomNode())
                {b'timeout': b'1000'}
            >>> client.config_get([b"timeout" , "maxmemory"])
                {b'timeout': b'1000', b'maxmemory': b'1GB'}
        """
        return cast(
            Dict[bytes, bytes],
            self._execute_command(RequestType.ConfigGet, parameters),
        )

    def config_set(self, parameters_map: Mapping[TEncodable, TEncodable]) -> TOK:
        """
        Set configuration parameters to the specified values.
        Starting from server version 7, command supports multiple parameters.

        See [valkey.io](https://valkey.io/commands/config-set/) for details.

        Args:
            parameters_map (Mapping[TEncodable, TEncodable]): A map consisting of configuration
                parameters and their respective values to set.

        Returns:
            OK: Returns OK if all configurations have been successfully set.

            Otherwise, raises an error.

        Examples:
            >>> config_set({"timeout": "1000", "maxmemory": "1GB"})
                OK
        """
        parameters: List[TEncodable] = []
        for pair in parameters_map.items():
            parameters.extend(pair)
        return cast(TOK, self._execute_command(RequestType.ConfigSet, parameters))

    def client_getname(self) -> Optional[bytes]:
        """
        Get the name of the primary's connection.

        See [valkey.io](https://valkey.io/commands/client-getname/) for more details.

        Returns:
            Optional[bytes]: Returns the name of the client connection as a byte string if a name is set.

            `None` if no name is assigned.

        Examples:
            >>> client.client_getname()
                b'Connection Name'
        """
        return cast(
            Optional[bytes], self._execute_command(RequestType.ClientGetName, [])
        )

    def dbsize(self) -> int:
        """
        Returns the number of keys in the currently selected database.

        See [valkey.io](https://valkey.io/commands/dbsize) for more details.

        Returns:
            int: The number of keys in the currently selected database.

        Examples:
            >>> client.dbsize()
                10  # Indicates there are 10 keys in the current database.
        """
        return cast(int, self._execute_command(RequestType.DBSize, []))

    def echo(self, message: TEncodable) -> bytes:
        """
        Echoes the provided `message` back.

        See [valkey.io](https://valkey.io/commands/echo) for more details.

        Args:
            message (TEncodable): The message to be echoed back.

        Returns:
            bytes: The provided `message`.

        Examples:
            >>> client.echo("Valkey GLIDE")
                b'Valkey GLIDE'
        """
        return cast(bytes, self._execute_command(RequestType.Echo, [message]))

    def function_load(self, library_code: TEncodable, replace: bool = False) -> bytes:
        """
        Loads a library to Valkey.

        See [valkey.io](https://valkey.io/commands/function-load/) for more details.

        Args:
            library_code (TEncodable): The source code that implements the library.
            replace (bool): Whether the given library should overwrite a library with the same name if
                it already exists.

        Returns:
            bytes: The library name that was loaded.

        Examples:
            >>> code = "#!lua name=mylib \\n redis.register_function('myfunc', function(keys, args) return args[1] end)"
            >>> client.function_load(code, True)
                b"mylib"

        Since: Valkey 7.0.0.
        """
        return cast(
            bytes,
            self._execute_command(
                RequestType.FunctionLoad,
                ["REPLACE", library_code] if replace else [library_code],
            ),
        )

    def function_list(
        self, library_name_pattern: Optional[TEncodable] = None, with_code: bool = False
    ) -> TFunctionListResponse:
        """
        Returns information about the functions and libraries.

        See [valkey.io](https://valkey.io/commands/function-list/) for more details.

        Args:
            library_name_pattern (Optional[TEncodable]):  A wildcard pattern for matching library names.
            with_code (bool): Specifies whether to request the library code from the server or not.

        Returns:
            TFunctionListResponse: Info about all or
            selected libraries and their functions.

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
                    b"library_code": b"#!lua name=mylib \\n sever.register_function('myfunc', function(keys, args) " \
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
            TFunctionListResponse,
            self._execute_command(
                RequestType.FunctionList,
                args,
            ),
        )

    def function_flush(self, mode: Optional[FlushMode] = None) -> TOK:
        """
        Deletes all function libraries.

        See [valkey.io](https://valkey.io/commands/function-flush/) for more details.

        Args:
            mode (Optional[FlushMode]): The flushing mode, could be either `SYNC` or `ASYNC`.

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
            ),
        )

    def function_delete(self, library_name: TEncodable) -> TOK:
        """
        Deletes a library and all its functions.

        See [valkey.io](https://valkey.io/commands/function-delete/) for more details.

        Args:
            library_code (TEncodable): The library name to delete

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
            ),
        )

    def function_kill(self) -> TOK:
        """
        Kills a function that is currently executing.
        This command only terminates read-only functions.

        FUNCTION KILL runs on all nodes of the server, including primary and replicas.

        See [valkey.io](https://valkey.io/commands/function-kill/) for more details.

        Returns:
            TOK: A simple `OK`.

        Examples:
            >>> client.function_kill()
                "OK"

        Since: Valkey 7.0.0.
        """
        return cast(
            TOK,
            self._execute_command(RequestType.FunctionKill, []),
        )

    def function_stats(self) -> TFunctionStatsFullResponse:
        """
        Returns information about the function that's currently running and information about the
        available execution engines.

        FUNCTION STATS runs on all nodes of the server, including primary and replicas.
        The response includes a mapping from node address to the command response for that node.

        See [valkey.io](https://valkey.io/commands/function-stats/) for more details

        Returns:
            TFunctionStatsFullResponse: A Map where the key is the node address and the value is a Map of two keys:

                - `running_script` with information about the running script.
                - `engines` with information about available engines and their stats.

            See example for more details.

        Examples:
            >>> client.function_stats()
                {b"addr": {                         # Response from the master node
                    b'running_script': {
                        b'name': b'foo',
                        b'command': [b'FCALL', b'foo', b'0', b'hello'],
                        b'duration_ms': 7758
                    },
                    b'engines': {
                        b'LUA': {
                            b'libraries_count': 1,
                            b'functions_count': 1,
                        }
                    }
                },
                b"addr2": {                         # Response from a replica
                    b'running_script': None,
                    b"engines": {
                        b'LUA': {
                            b'libraries_count': 1,
                            b'functions_count': 1,
                        }
                    }
                }}

        Since: Valkey version 7.0.0.
        """
        return cast(
            TFunctionStatsFullResponse,
            self._execute_command(RequestType.FunctionStats, []),
        )

    def function_dump(self) -> bytes:
        """
        Returns the serialized payload of all loaded libraries.

        See [valkey.io](https://valkey.io/docs/latest/commands/function-dump/) for more details.

        Returns:
            bytes: The serialized payload of all loaded libraries.

        Examples:
            >>> payload = client.function_dump()
                # The serialized payload of all loaded libraries. This response can
                # be used to restore loaded functions on any Valkey instance.
            >>> client.function_restore(payload)
                "OK" # The serialized dump response was used to restore the libraries.

        Since: Valkey 7.0.0.
        """
        return cast(bytes, self._execute_command(RequestType.FunctionDump, []))

    def function_restore(
        self, payload: TEncodable, policy: Optional[FunctionRestorePolicy] = None
    ) -> TOK:
        """
        Restores libraries from the serialized payload returned by the `function_dump` command.

        See [valkey.io](https://valkey.io/docs/latest/commands/function-restore/) for more details.

        Args:
            payload (TEncodable): The serialized data from the `function_dump` command.
            policy (Optional[FunctionRestorePolicy]): A policy for handling existing libraries.

        Returns:
            TOK: OK.

        Examples:
            >>> payload = client.function_dump()
                # The serialized payload of all loaded libraries. This response can
                # be used to restore loaded functions on any Valkey instance.
            >>> client.function_restore(payload)
                "OK" # The serialized dump response was used to restore the libraries.
            >>> client.function_restore(payload, FunctionRestorePolicy.FLUSH)
                "OK" # The serialized dump response was used to restore the libraries with the specified policy.

        Since: Valkey 7.0.0.
        """
        args: List[TEncodable] = [payload]
        if policy is not None:
            args.append(policy.value)

        return cast(TOK, self._execute_command(RequestType.FunctionRestore, args))

    def time(self) -> List[bytes]:
        """
        Returns the server time.

        See [valkey.io](https://valkey.io/commands/time/) for more details.

        Returns:
            List[bytes]:  The current server time as a two items `array`:
            A Unix timestamp and the amount of microseconds already elapsed in the current second.
            The returned `array` is in a [Unix timestamp, Microseconds already elapsed] format.

        Examples:
            >>> client.time()
                [b'1710925775', b'913580']
        """
        return cast(
            List[bytes],
            self._execute_command(RequestType.Time, []),
        )

    def lastsave(self) -> int:
        """
        Returns the Unix time of the last DB save timestamp or startup timestamp if no save was made since then.

        See [valkey.io](https://valkey.io/commands/lastsave) for more details.

        Returns:
            int: The Unix time of the last successful DB save.

        Examples:
            >>> client.lastsave()
                1710925775  # Unix time of the last DB save
        """
        return cast(
            int,
            self._execute_command(RequestType.LastSave, []),
        )

    def move(self, key: TEncodable, db_index: int) -> bool:
        """
        Move `key` from the currently selected database to the database specified by `db_index`.

        See [valkey.io](https://valkey.io/commands/move/) for more details.

        Args:
            key (TEncodable): The key to move.
            db_index (int): The index of the database to move `key` to.

        Returns:
            bool: `True` if `key` was moved.

            `False` if the `key` already exists in the destination database
            or does not exist in the source database.

        Example:
            >>> client.move("some_key", 1)
                True
        """
        return cast(
            bool,
            self._execute_command(RequestType.Move, [key, str(db_index)]),
        )

    def flushall(self, flush_mode: Optional[FlushMode] = None) -> TOK:
        """
        Deletes all the keys of all the existing databases. This command never fails.

        See [valkey.io](https://valkey.io/commands/flushall) for more details.

        Args:
            flush_mode (Optional[FlushMode]): The flushing mode, could be either `SYNC` or `ASYNC`.

        Returns:
            TOK: A simple OK response.

        Examples:
            >>> client.flushall(FlushMode.ASYNC)
                OK  # This command never fails.
        """
        args: List[TEncodable] = []
        if flush_mode is not None:
            args.append(flush_mode.value)

        return cast(
            TOK,
            self._execute_command(RequestType.FlushAll, args),
        )

    def flushdb(self, flush_mode: Optional[FlushMode] = None) -> TOK:
        """
        Deletes all the keys of the currently selected database. This command never fails.

        See [valkey.io](https://valkey.io/commands/flushdb) for more details.

        Args:
            flush_mode (Optional[FlushMode]): The flushing mode, could be either `SYNC` or `ASYNC`.

        Returns:
            TOK: A simple OK response.

        Examples:
            >>> client.flushdb()
                OK  # The keys of the currently selected database were deleted.
            >>> client.flushdb(FlushMode.ASYNC)
                OK  # The keys of the currently selected database were deleted asynchronously.
        """
        args: List[TEncodable] = []
        if flush_mode is not None:
            args.append(flush_mode.value)

        return cast(
            TOK,
            self._execute_command(RequestType.FlushDB, args),
        )

    def copy(
        self,
        source: TEncodable,
        destination: TEncodable,
        destinationDB: Optional[int] = None,
        replace: Optional[bool] = None,
    ) -> bool:
        """
        Copies the value stored at the `source` to the `destination` key. If `destinationDB`
        is specified, the value will be copied to the database specified by `destinationDB`,
        otherwise the current database will be used. When `replace` is True, removes the
        `destination` key first if it already exists, otherwise performs no action.

        See [valkey.io](https://valkey.io/commands/copy) for more details.

        Args:
            source (TEncodable): The key to the source value.
            destination (TEncodable): The key where the value should be copied to.
            destinationDB (Optional[int]): The alternative logical database index for the destination key.
            replace (Optional[bool]): If the destination key should be removed before copying the value to it.

        Returns:
            bool: True if the source was copied.

            Otherwise, return False.

        Examples:
            >>> client.set("source", "sheep")
            >>> client.copy(b"source", b"destination", 1, False)
                True # Source was copied
            >>> client.select(1)
            >>> client.get("destination")
                b"sheep"

        Since: Valkey version 6.2.0.
        """
        args: List[TEncodable] = [source, destination]
        if destinationDB is not None:
            args.extend(["DB", str(destinationDB)])
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
    ) -> bytes:
        """
        Displays a piece of generative computer art and the Valkey version.

        See [valkey.io](https://valkey.io/commands/lolwut) for more details.

        Args:
            version (Optional[int]): Version of computer art to generate.
            parameters (Optional[List[int]]): Additional set of arguments in order to change the output:

                - For version `5`, those are length of the line, number of squares per row, and number of squares per column.
                - For version `6`, those are number of columns and number of lines.

        Returns:
            bytes: A piece of generative computer art along with the current Valkey version.

        Examples:
            >>> client.lolwut(6, [40, 20]);
                b"Redis ver. 7.2.3" # Indicates the current Valkey version
            >>> client.lolwut(5, [30, 5, 5]);
                b"Redis ver. 7.2.3" # Indicates the current Valkey version
        """
        args: List[TEncodable] = []
        if version is not None:
            args.extend(["VERSION", str(version)])
        if parameters:
            for var in parameters:
                args.extend(str(var))
        return cast(
            bytes,
            self._execute_command(RequestType.Lolwut, args),
        )

    def random_key(self) -> Optional[bytes]:
        """
        Returns a random existing key name from the currently selected database.

        See [valkey.io](https://valkey.io/commands/randomkey) for more details.

        Returns:
            Optional[bytes]: A random existing key name from the currently selected database.

        Examples:
            >>> client.random_key()
                b"random_key_name"  # "random_key_name" is a random existing key name from the currently selected database.
        """
        return cast(
            Optional[bytes],
            self._execute_command(RequestType.RandomKey, []),
        )

    def wait(
        self,
        numreplicas: int,
        timeout: int,
    ) -> int:
        """
        Blocks the current client until all the previous write commands are successfully transferred
        and acknowledged by at least `numreplicas` of replicas. If `timeout` is
        reached, the command returns even if the specified number of replicas were not yet reached.

        See [valkey.io](https://valkey.io/commands/wait) for more details.

        Args:
            numreplicas (int): The number of replicas to reach.
            timeout (int): The timeout value specified in milliseconds. A value of 0 will block indefinitely.

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
            self._execute_command(RequestType.Wait, args),
        )
