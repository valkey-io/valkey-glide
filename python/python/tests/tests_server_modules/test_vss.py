import pytest
from typing import List
from glide.async_commands.server_modules import vss
from glide.config import ProtocolVersion
from glide.glide_client import TGlideClient
from glide.async_commands.server_modules.vss_options import FtCreateOptions, DataType, FieldInfo, FieldType
from glide.constants import OK

@pytest.mark.asyncio
class TestVss:
    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_vss_create(self, glide_client: TGlideClient):        
        index = "idx"
        fields: List[FieldInfo] = []
        fieldInfo1: FieldInfo = FieldInfo("title", None, FieldType.TEXT, True)
        fieldInfo2: FieldInfo = FieldInfo("published_at", None, FieldType.NUMERIC, True)
        fieldInfo3: FieldInfo = FieldInfo("category", None, FieldType.TAG, True)
        fields.append(fieldInfo1)
        fields.append(fieldInfo2)
        fields.append(fieldInfo3)
        options: FtCreateOptions = FtCreateOptions(DataType.HASH, fields)
        result = await vss.create(glide_client, index, options)
        assert result == OK
        
        # print info command result
        print(await vss.info(glide_client, index))
