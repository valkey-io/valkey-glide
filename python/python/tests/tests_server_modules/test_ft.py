from typing import List

import pytest
from glide.async_commands.server_modules import ft
from glide.async_commands.server_modules.ft_options.ft_create_options import (
    DataType,
    DistanceMetricType,
    Field,
    FtCreateOptions,
    NumericField,
    TextField,
    VectorAlgorithm,
    VectorField,
    VectorFieldAttributesHnsw,
    VectorType,
)
from glide.config import ProtocolVersion
from glide.constants import OK
from glide.glide_client import TGlideClient


@pytest.mark.asyncio
class TestVss:
    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_vss_create(self, glide_client: TGlideClient):
        fields: List[Field] = []
        field1: TextField = TextField("$title")
        field2: NumericField = NumericField("$published_at")
        field3: TextField = TextField("$category")
        fields.append(field1)
        fields.append(field2)
        fields.append(field3)

        prefixes: List[str] = []
        prefixes.append("blog:post:")

        """
        Create an index with multiple fields with Hash data type.
        """
        index = "idx"
        result = await ft.create(
            glide_client, index, fields, FtCreateOptions(DataType.HASH, prefixes)
        )
        assert result == OK

        """
        Create an index with multiple fields with JSON data type.
        """
        index2 = "idx2"
        result = await ft.create(
            glide_client, index2, fields, FtCreateOptions(DataType.JSON, prefixes)
        )
        assert result == OK

        """
        Create an index for vectors of size 2
        FT.CREATE hash_idx1 ON HASH PREFIX 1 hash: SCHEMA vec AS VEC VECTOR HNSW 6 DIM 2 TYPE FLOAT32 DISTANCE_METRIC L2
        """
        index3 = "hash_idx1"
        prefixes = []
        prefixes.append("hash:")
        fields = []
        vectorFieldHash: VectorField = VectorField(
            name="vec",
            algorithm=VectorAlgorithm.HNSW,
            attributes=VectorFieldAttributesHnsw(
                dim=2, distanceMetric=DistanceMetricType.L2, type=VectorType.FLOAT32
            ),
            alias="VEC",
        )
        fields.append(vectorFieldHash)

        result = await ft.create(
            glide_client, index3, fields, FtCreateOptions(DataType.HASH, prefixes)
        )
        assert result == OK

        """
        Create a 6-dimensional JSON index using the HNSW algorithm
        FT.CREATE json_idx1 ON JSON PREFIX 1 json: SCHEMA $.vec AS VEC VECTOR HNSW 6 DIM 6 TYPE FLOAT32 DISTANCE_METRIC L2
        """
        index4 = "json_idx1"
        prefixes = []
        prefixes.append("json:")
        fields = []
        vectorFieldJson: VectorField = VectorField(
            name="$.vec",
            algorithm=VectorAlgorithm.HNSW,
            attributes=VectorFieldAttributesHnsw(
                dim=6, distanceMetric=DistanceMetricType.L2, type=VectorType.FLOAT32
            ),
            alias="VEC",
        )
        fields.append(vectorFieldJson)

        result = await ft.create(
            glide_client, index4, fields, FtCreateOptions(DataType.JSON, prefixes)
        )
        assert result == OK

        """
        TO-DO:
        Add additional tests from VSS documentation that require a combination of commands to run.
        """
