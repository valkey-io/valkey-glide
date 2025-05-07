# Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0
from typing import List, Optional, Protocol, Tuple, cast

from glide.commands.command_args import ObjectType
from glide.commands.core_options import ConditionalChange, ExpirySet
from glide.constants import TOK, TEncodable, TResult
from glide.protobuf.command_request_pb2 import RequestType
from glide.routes import Route

from ...glide import ClusterScanCursor


class CoreCommands(Protocol):
    def _execute_command(
        self,
        request_type: RequestType.ValueType,
        args: List[TEncodable],
        route: Optional[Route] = ...,
    ) -> TResult: ...

    def _execute_transaction(
        self,
        commands: List[Tuple[RequestType.ValueType, List[TEncodable]]],
        route: Optional[Route] = None,
    ) -> List[TResult]: ...

    def _execute_script(
        self,
        hash: str,
        keys: Optional[List[TEncodable]] = None,
        args: Optional[List[TEncodable]] = None,
        route: Optional[Route] = None,
    ) -> TResult: ...

    def _cluster_scan(
        self,
        cursor: ClusterScanCursor,
        match: Optional[TEncodable] = ...,
        count: Optional[int] = ...,
        type: Optional[ObjectType] = ...,
        allow_non_covered_slots: bool = ...,
    ) -> TResult: ...

    def _update_connection_password(
        self, password: Optional[str], immediate_auth: bool
    ) -> TResult: ...

    def update_connection_password(
        self, password: Optional[str], immediate_auth=False
    ) -> TOK:
        """
        Update the current connection password with a new password.

        **Note:** This method updates the client's internal password configuration and does
        not perform password rotation on the server side.

        This method is useful in scenarios where the server password has changed or when
        utilizing short-lived passwords for enhanced security. It allows the client to
        update its password to reconnect upon disconnection without the need to recreate
        the client instance. This ensures that the internal reconnection mechanism can
        handle reconnection seamlessly, preventing the loss of in-flight commands.

        Args:
            password (`Optional[str]`): The new password to use for the connection,
            if `None` the password will be removed.
            immediate_auth (`bool`):
                `True`: The client will authenticate immediately with the new password against all connections, Using `AUTH`
                command. If password supplied is an empty string, auth will not be performed and warning will be returned.
                The default is `False`.

        Returns:
            TOK: A simple OK response. If `immediate_auth=True` returns OK if the reauthenticate succeed.

        Example:
            >>> client.update_connection_password("new_password", immediate_auth=True)
            'OK'
        """
        return cast(TOK, self._update_connection_password(password, immediate_auth))

    def set(
        self,
        key: TEncodable,
        value: TEncodable,
        conditional_set: Optional[ConditionalChange] = None,
        expiry: Optional[ExpirySet] = None,
        return_old_value: bool = False,
    ) -> Optional[bytes]:
        """
        Set the given key with the given value. Return value is dependent on the passed options.

        See [valkey.io](https://valkey.io/commands/set/) for more details.

        Args:
            key (TEncodable): the key to store.
            value (TEncodable): the value to store with the given key.
            conditional_set (Optional[ConditionalChange], optional): set the key only if the given condition is met.
                Equivalent to [`XX` | `NX` | `IFEQ` comparison-value] in the Valkey API. Defaults to None.
            expiry (Optional[ExpirySet], optional): set expiriation to the given key.
                Equivalent to [`EX` | `PX` | `EXAT` | `PXAT` | `KEEPTTL`] in the Valkey API. Defaults to None.
            return_old_value (bool, optional): Return the old value stored at key, or None if key did not exist.
                An error is returned and SET aborted if the value stored at key is not a string.
                Equivalent to `GET` in the Valkey API. Defaults to False.

        Returns:
            Optional[bytes]: If the value is successfully set, return OK.

            If value isn't set because of `only_if_exists` or `only_if_does_not_exist` conditions, return `None`.

            If return_old_value is set, return the old value as a bytes string.

        Example:
            >>> client.set(b"key", b"value")
                'OK'
                # ONLY_IF_EXISTS -> Only set the key if it already exists
                # expiry -> Set the amount of time until key expires
            >>> client.set(
            ...     "key",
            ...     "new_value",
            ...     conditional_set=ConditionalChange.ONLY_IF_EXISTS,
            ...     expiry=ExpirySet(ExpiryType.SEC, 5)
            ... )
                'OK' # Set "new_value" to "key" only if "key" already exists, and set the key expiration to 5 seconds.
                # ONLY_IF_DOES_NOT_EXIST -> Only set key if it does not already exist
            >>> client.set(
            ...     "key",
            ...     "value",
            ...     conditional_set=ConditionalChange.ONLY_IF_DOES_NOT_EXIST,
            ...     return_old_value=True
            ... )
                b'new_value' # Returns the old value of "key".
            >>> client.get("key")
                b'new_value' # Value wasn't modified back to being "value" because of "NX" flag.
                # ONLY_IF_EQUAL -> Only set key if provided value is equal to current value of the key
            >>> client.set("key", "value")
                'OK' # Reset "key" to "value"
            >>> client.set("key", "new_value", conditional_set=OnlyIfEqual("different_value"))
                'None' # Did not rewrite value of "key" because provided value was not equal to the previous value of "key"
            >>> client.get("key")
                b'value' # Still the original value because nothing got rewritten in the last call
            >>> client.set("key", "new_value", conditional_set=OnlyIfEqual("value"))
                'OK'
            >>> client.get("key")
                b'newest_value' # Set "key" to "new_value" because the provided value was equal to the previous value of "key"
        """
        args = [key, value]
        if conditional_set:
            args.append(conditional_set.value)
        if return_old_value:
            args.append("GET")
        if expiry is not None:
            args.extend(expiry.get_cmd_args())
        return cast(Optional[bytes], self._execute_command(RequestType.Set, args))

    def get(self, key: TEncodable) -> Optional[bytes]:
        """
        Get the value associated with the given key, or null if no such value exists.
        See https://valkey.io/commands/get/ for details.

        Args:
            key (TEncodable): The key to retrieve from the database.

        Returns:
            Optional[bytes]: If the key exists, returns the value of the key as a byte string. Otherwise, return None.

        Example:
            >>> client.get("key")
                b'value'
        """
        args: List[TEncodable] = [key]
        return cast(Optional[bytes], self._execute_command(RequestType.Get, args))
