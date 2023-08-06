from pybushka.constants import TConnectionRequest
from pybushka.redis_client import RedisClient


class RedisClusterClient(RedisClient):
    def _get_protobuf_conn_request(self) -> TConnectionRequest:
        return self.config.convert_to_protobuf_request(cluster_mode=True)
