# Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0
import uuid
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
from glide.glide_client import GlideClusterClient


@pytest.mark.asyncio
class TestVss:
    @pytest.mark.parametrize("cluster_mode", [True])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_vss_create(self, glide_client: GlideClusterClient):
        fields: List[Field] = []
        textFieldTitle: TextField = TextField("$title")
        numberField: NumericField = NumericField("$published_at")
        textFieldCategory: TextField = TextField("$category")
        fields.append(textFieldTitle)
        fields.append(numberField)
        fields.append(textFieldCategory)

        prefixes: List[str] = []
        prefixes.append("blog:post:")

        # Create an index with multiple fields with Hash data type.
        index = str(uuid.uuid4())
        result = await ft.create(
            glide_client, index, fields, FtCreateOptions(DataType.HASH, prefixes)
        )
        assert result == OK

        # Create an index with multiple fields with JSON data type.
        index2 = str(uuid.uuid4())
        result = await ft.create(
            glide_client, index2, fields, FtCreateOptions(DataType.JSON, prefixes)
        )
        assert result == OK

        # Create an index for vectors of size 2
        # FT.CREATE hash_idx1 ON HASH PREFIX 1 hash: SCHEMA vec AS VEC VECTOR HNSW 6 DIM 2 TYPE FLOAT32 DISTANCE_METRIC L2
        index3 = str(uuid.uuid4())
        prefixes = []
        prefixes.append("hash:")
        fields = []
        vectorFieldHash: VectorField = VectorField(
            name="vec",
            algorithm=VectorAlgorithm.HNSW,
            attributes=VectorFieldAttributesHnsw(
                dim=2, distance_metric=DistanceMetricType.L2, type=VectorType.FLOAT32
            ),
            alias="VEC",
        )
        fields.append(vectorFieldHash)

        result = await ft.create(
            glide_client, index3, fields, FtCreateOptions(DataType.HASH, prefixes)
        )
        assert result == OK

        # Create a 6-dimensional JSON index using the HNSW algorithm
        # FT.CREATE json_idx1 ON JSON PREFIX 1 json: SCHEMA $.vec AS VEC VECTOR HNSW 6 DIM 6 TYPE FLOAT32 DISTANCE_METRIC L2
        index4 = str(uuid.uuid4())
        prefixes = []
        prefixes.append("json:")
        fields = []
        vectorFieldJson: VectorField = VectorField(
            name="$.vec",
            algorithm=VectorAlgorithm.HNSW,
            attributes=VectorFieldAttributesHnsw(
                dim=6, distance_metric=DistanceMetricType.L2, type=VectorType.FLOAT32
            ),
            alias="VEC",
        )
        fields.append(vectorFieldJson)

        result = await ft.create(
            glide_client, index4, fields, FtCreateOptions(DataType.JSON, prefixes)
        )
        assert result == OK

        # Create an index without FtCreateOptions

        index5 = str(uuid.uuid4())
        result = await ft.create(glide_client, index5, fields, FtCreateOptions())
        assert result == OK

        # TO-DO:
        # Add additional tests from VSS documentation that require a combination of commands to run.
