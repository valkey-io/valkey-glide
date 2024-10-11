import uuid
from typing import List

import pytest
from glide.async_commands.server_modules import ft
from glide.async_commands.server_modules.ft_options.ft_create_options import (
    DataType,
    Field,
    FtCreateOptions,
    TextField,
)
from glide.config import ProtocolVersion
from glide.constants import OK
from glide.exceptions import RequestError
from glide.glide_client import GlideClusterClient


@pytest.mark.asyncio
class TestFtDropIndex:
    @pytest.mark.parametrize("cluster_mode", [True])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_ft_dropindex(self, glide_client: GlideClusterClient):
        # Index name for the index to be dropped.
        indexName = str(uuid.uuid4())

        fields: List[Field] = []
        textFieldTitle: TextField = TextField("$title")
        fields.append(textFieldTitle)
        prefixes: List[str] = []
        prefixes.append("blog:post:")

        # Create an index with multiple fields with Hash data type.
        result = await ft.create(
            glide_client, indexName, fields, FtCreateOptions(DataType.HASH, prefixes)
        )
        assert result == OK

        # Drop the index. Expects "OK" as a response.
        result = await ft.dropindex(glide_client, indexName)
        assert result == OK

        # Drop a non existent index. Expects a RequestError.
        with pytest.raises(RequestError):
            await ft.dropindex(glide_client, indexName)
