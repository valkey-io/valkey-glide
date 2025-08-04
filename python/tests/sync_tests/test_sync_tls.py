import pytest
from glide_shared.config import ProtocolVersion
from glide_sync.glide_client import TGlideClient


class TestSyncTls:
    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    @pytest.mark.parametrize("tls_insecure", [True], indirect=True)
    def test_sync_tls_insecure(self, glide_sync_tls_client: TGlideClient):
        """
        This test verifies that the Glide client can connect to a TLS-enabled Valkey instance while skipping
        certificate validation. By parametrizing tls_insecure=True, we confirm that the client can successfully
        ping the cluster without strict cert checks.
        """
        result = glide_sync_tls_client.ping()

        assert result == b"PONG"
