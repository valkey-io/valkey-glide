from pybushka.config import AddressInfo, ClientConfiguration, ReadFromReplica
from pybushka.protobuf.connection_request_pb2 import (
    ConnectionRequest,
    ReadFromReplicaStrategy,
    TlsMode,
)


def test_default_client_config():
    config = ClientConfiguration()
    assert config.addresses[0].host == "localhost"
    assert config.addresses[0].port == 6379
    assert config.read_from_replica.value == ReadFromReplicaStrategy.AlwaysFromPrimary
    assert config.use_tls is False


def test_convert_to_protobuf():
    config = ClientConfiguration(
        [AddressInfo("127.0.0.1")],
        use_tls=True,
        read_from_replica=ReadFromReplica.ROUND_ROBIN,
    )
    request = config._create_a_protobuf_conn_request()
    assert isinstance(request, ConnectionRequest)
    assert request.addresses[0].host == "127.0.0.1"
    assert request.addresses[0].port == 6379
    assert request.tls_mode is TlsMode.SecureTls
    assert request.read_from_replica_strategy == ReadFromReplicaStrategy.RoundRobin
