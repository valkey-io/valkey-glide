# Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0
# mypy: disable_error_code="arg-type"

from __future__ import annotations

import pytest
from glide.config import ProtocolVersion
from glide.constants import OK
from glide.glide_sync import TGlideClient

from tests.utils.utils import get_random_string


class TestGlideClients:
    @pytest.mark.skip_if_version_below("6.2.0")
    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    def test_sync_set_return_old_value(self, glide_sync_client: TGlideClient):
        key = get_random_string(10)
        value = get_random_string(10)
        res = glide_sync_client.set(key, value)
        assert res == OK
        assert glide_sync_client.get(key) == value.encode()
        new_value = get_random_string(10)
        res = glide_sync_client.set(key, new_value, return_old_value=True)
        assert res == value.encode()
        assert glide_sync_client.get(key) == new_value.encode()
