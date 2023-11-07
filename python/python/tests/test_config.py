from pybushka.config import BaseClientConfiguration, NodeAddress, ReadFrom
from pybushka.protobuf.connection_request_pb2 import ConnectionRequest
from pybushka.protobuf.connection_request_pb2 import ReadFrom as ProtobufReadFrom
from pybushka.protobuf.connection_request_pb2 import TlsMode


def test_default_client_config():
    config = BaseClientConfiguration()
    assert config.addresses[0].host == "localhost"
    assert config.addresses[0].port == 6379
    assert config.read_from.value == ProtobufReadFrom.Primary
    assert config.use_tls is False


def test_convert_to_protobuf():
    config = BaseClientConfiguration(
        [NodeAddress("127.0.0.1")],
        use_tls=True,
        read_from=ReadFrom.PREFER_REPLICA,
    )
    request = config._create_a_protobuf_conn_request()
    assert isinstance(request, ConnectionRequest)
    assert request.addresses[0].host == "127.0.0.1"
    assert request.addresses[0].port == 6379
    assert request.tls_mode is TlsMode.SecureTls
    assert request.read_from == ProtobufReadFrom.PreferReplica
