# Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

from typing import Optional

from glide_shared.constants import TSingleNodeRoute


class BatchRetryStrategy:
    """
    Defines a retry strategy for cluster batch requests, allowing control over retries in case of
    server or connection errors.

    This strategy determines whether failed commands should be retried, impacting execution order
    and potential side effects.

    Behavior:
        - If `retry_server_error` is `True`, failed commands with a retriable error (e.g.,
          `TRYAGAIN`) will be retried.
        - If `retry_connection_error` is `True`, batch requests will be retried on
          connection failures.

    Cautions:
        - **Server Errors:** Retrying may cause commands targeting the same slot to be executed
          out of order.
        - **Connection Errors:** Retrying may lead to duplicate executions, since the server might
          have already received and processed the request before the error occurred.

    Example Scenario:
        ```
        MGET key {key}:1
        SET key "value"
        ```

        Expected response when keys are empty:
        ```
        [None, None]
        "OK"
        ```

        However, if the slot is migrating, both commands may return an `ASK` error and be
        redirected. Upon `ASK` redirection, a multi-key command may return a `TRYAGAIN`
        error (triggering a retry), while the `SET` command succeeds immediately. This
        can result in an unintended reordering of commands if the first command is retried
        after the slot stabilizes:
        ```
        ["value", None]
        "OK"
        ```

    Note:
        Currently, retry strategies are supported only for non-atomic batches.

    Default:
        Both `retry_server_error` and `retry_connection_error` are set to `False`.

    Args:
        retry_server_error (bool): If `True`, failed commands with a retriable error (e.g., `TRYAGAIN`)
            will be automatically retried.

            ⚠️ **Warning:** Enabling this flag may cause commands targeting the same slot to execute
            out of order.

            By default, this is set to `False`.

        retry_connection_error (bool): If `True`, batch requests will be retried in case of connection errors.

            ⚠️ **Warning:** Retrying after a connection error may lead to duplicate executions, since
            the server might have already received and processed the request before the error occurred.

            By default, this is set to `False`.

    """

    def __init__(
        self,
        retry_server_error: bool = False,
        retry_connection_error: bool = False,
    ):
        """
        Initialize a BatchRetryStrategy.

        Args:
            retry_server_error (bool): If `True`, failed commands with a retriable error (e.g., `TRYAGAIN`)
                will be automatically retried.

                ⚠️ **Warning:** Enabling this flag may cause commands targeting the same slot to execute
                out of order.

                By default, this is set to `False`.

            retry_connection_error (bool): If `True`, batch requests will be retried in case of connection errors.

                ⚠️ **Warning:** Retrying after a connection error may lead to duplicate executions, since
                the server might have already received and processed the request before the error occurred.

                By default, this is set to `False`.

        """
        self.retry_server_error = retry_server_error
        self.retry_connection_error = retry_connection_error


class BaseBatchOptions:
    """
    Base options settings class for sending a batch request. Shared settings for standalone and
    cluster batch requests.

    Args:
        timeout (Optional[int]): The duration in milliseconds that the client should wait for the batch request
            to complete. This duration encompasses sending the request, awaiting a response from the server,
            and any required reconnections or retries. If the specified timeout is exceeded for a pending request,
            it will result in a timeout error. If not explicitly set, the client's default request timeout will be used.
    """

    def __init__(
        self,
        timeout: Optional[int] = None,
    ):
        """
        Initialize BaseBatchOptions.

        Args:
            timeout (Optional[int]): The duration in milliseconds that the client should wait for the batch request
                to complete. This duration encompasses sending the request, awaiting a response from the server,
                and any required reconnections or retries. If the specified timeout is exceeded for a pending request,
                it will result in a timeout error. If not explicitly set, the client's default request timeout will be used.
        """
        self.timeout = timeout


class BatchOptions(BaseBatchOptions):
    """
    Options for a batch request for a standalone client.

    Args:
        timeout (Optional[int]): The duration in milliseconds that the client should wait for the batch request
            to complete. This duration encompasses sending the request, awaiting a response from the server,
            and any required reconnections or retries. If the specified timeout is exceeded for a pending request,
            it will result in a timeout error. If not explicitly set, the client's default request timeout will be used.
    """

    def __init__(
        self,
        timeout: Optional[int] = None,
    ):
        """
        Options for a batch request for a standalone client

        Args:
            timeout (Optional[int]): The duration in milliseconds that the client should wait for the batch request
                to complete. This duration encompasses sending the request, awaiting a response from the server,
                and any required reconnections or retries. If the specified timeout is exceeded for a pending request,
                it will result in a timeout error. If not explicitly set, the client's default request timeout will be used.
        """
        super().__init__(timeout)


class ClusterBatchOptions(BaseBatchOptions):
    """
    Options for cluster batch operations.

    Args:
        timeout (Optional[int]): The duration in milliseconds that the client should wait for the batch request
            to complete. This duration encompasses sending the request, awaiting a response from the server,
            and any required reconnections or retries. If the specified timeout is exceeded for a pending request,
            it will result in a timeout error. If not explicitly set, the client's default request timeout will be used.

        route (Optional[TSingleNodeRoute]): Configures single-node routing for the batch request. The client
            will send the batch to the specified node defined by `route`.

            If a redirection error occurs:

            - For Atomic Batches (Transactions), the entire transaction will be redirected.
            - For Non-Atomic Batches (Pipelines), only the commands that encountered redirection errors
              will be redirected.

        retry_strategy (Optional[BatchRetryStrategy]): ⚠️ **Please see `BatchRetryStrategy` and read carefully before enabling these
            options.**

            Defines the retry strategy for handling cluster batch request failures.

            This strategy determines whether failed commands should be retried, potentially impacting
            execution order.

            - If `retry_server_error` is `True`, retriable errors (e.g., TRYAGAIN) will
              trigger a retry.
            - If `retry_connection_error` is `True`, connection failures will trigger a
              retry.

            **Warnings:**

            - Retrying server errors may cause commands targeting the same slot to execute out of
              order.
            - Retrying connection errors may lead to duplicate executions, as it is unclear which
              commands have already been processed.

            **Note:** Currently, retry strategies are supported only for non-atomic batches.

            **Recommendation:** It is recommended to increase the timeout in `timeout`
            when enabling these strategies.

            **Default:** Both `retry_server_error` and `retry_connection_error` are set to
            `False`.

    """

    def __init__(
        self,
        timeout: Optional[int] = None,
        route: Optional[TSingleNodeRoute] = None,
        retry_strategy: Optional[BatchRetryStrategy] = None,
    ):
        """
        Initialize ClusterBatchOptions.

        Args:
            timeout (Optional[int]): The duration in milliseconds that the client should wait for the batch request
                to complete. This duration encompasses sending the request, awaiting a response from the server,
                and any required reconnections or retries. If the specified timeout is exceeded for a pending request,
                it will result in a timeout error. If not explicitly set, the client's default request timeout will be used.

            route (Optional[TSingleNodeRoute]): Configures single-node routing for the batch request. The client
                will send the batch to the specified node defined by `route`.

                If a redirection error occurs:

                - For Atomic Batches (Transactions), the entire transaction will be redirected.
                - For Non-Atomic Batches (Pipelines), only the commands that encountered redirection errors
                will be redirected.

            retry_strategy (Optional[BatchRetryStrategy]): ⚠️ **Please see `BatchRetryStrategy` and read carefully before enabling these
                options.**

                Defines the retry strategy for handling cluster batch request failures.

                This strategy determines whether failed commands should be retried, potentially impacting
                execution order.

                - If `retry_server_error` is `True`, retriable errors (e.g., TRYAGAIN) will
                trigger a retry.
                - If `retry_connection_error` is `True`, connection failures will trigger a
                retry.

                **Warnings:**

                - Retrying server errors may cause commands targeting the same slot to execute out of
                order.
                - Retrying connection errors may lead to duplicate executions, as it is unclear which
                commands have already been processed.

                **Note:** Currently, retry strategies are supported only for non-atomic batches.

                **Recommendation:** It is recommended to increase the timeout in `timeout`
                when enabling these strategies.

                **Default:** Both `retry_server_error` and `retry_connection_error` are set to
                `False`.
        """
        super().__init__(timeout)
        self.retry_strategy = retry_strategy
        self.route = route
