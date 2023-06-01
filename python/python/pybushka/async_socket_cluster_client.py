from pybushka.async_socket_client import RedisAsyncSocketClient
from pybushka.constants import TConnectionRequest


class RedisClusterAsyncSocket(RedisAsyncSocketClient):
    def _get_protobuf_conn_request(self) -> TConnectionRequest:
        return self.config.convert_to_protobuf_request(cluster_mode=True)
