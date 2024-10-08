import pytest
from typing import List
from glide.async_commands.server_modules import search
from glide.config import ProtocolVersion
from glide.glide_client import TGlideClient
from glide.async_commands.server_modules.search_options.ft_create_options import FtCreateOptions, DataType, FieldInfo, FieldType, SORTABLE, FieldTypeInfo, VectorTypeOptions, VectorTypeAlgorithm, VectorTypeHnswAttributes, VectorTypeFlatAttributes, DistanceMetricType, VectorType
from glide.constants import OK

@pytest.mark.asyncio
class TestVss:
    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_vss_create(self, glide_client: TGlideClient):
        fields: List[FieldInfo] = []
        fieldInfo1: FieldInfo = FieldInfo(
            "title", 
            fieldTypeInfo=FieldTypeInfo(fieldType=FieldType.TEXT),
            sortable = SORTABLE.IS_SORTABLE
        )
        fieldInfo2: FieldInfo = FieldInfo(
            "published_at",
            FieldTypeInfo(fieldType=FieldType.NUMERIC),
            sortable = SORTABLE.IS_SORTABLE
        )
        fieldInfo3: FieldInfo = FieldInfo(
            "category",
            FieldTypeInfo(fieldType=FieldType.TEXT),
            sortable = SORTABLE.IS_SORTABLE
        )
        fields.append(fieldInfo1)
        fields.append(fieldInfo2)
        fields.append(fieldInfo3)

        prefixes: List[str] = []
        prefixes.append("blog:post:")

        """
        Create an index with multiple fields with Hash data type.
        """
        index = "idx"
        result = await search.create(glide_client, index, fields, FtCreateOptions(DataType.HASH, prefixes))
        print(result)
        assert result == OK

        """
        Create an index with multiple fields with JSON data type.
        """
        index2 = "idx2"
        result = await search.create(glide_client, index2, fields, FtCreateOptions(DataType.JSON, prefixes))
        assert result == OK
  
        """
        Create an index for vectors of size 2
        FT.CREATE hash_idx1 ON HASH PREFIX 1 hash: SCHEMA vec AS VEC VECTOR HNSW 6 DIM 2 TYPE FLOAT32 DISTANCE_METRIC L2
        """
        index3 = "hash_idx1"
        prefixes = []
        prefixes.append("hash:")
        fields = []
        fieldInfo: FieldInfo = FieldInfo(
            identifier = "vec",
            fieldTypeInfo = FieldTypeInfo(
                FieldType.VECTOR,
                vectorTypeOptions = VectorTypeOptions(
                    VectorTypeAlgorithm.HNSW,
                    vectorTypeHnswAttributes = VectorTypeHnswAttributes(
                        dim = 2,
                        distanceMetric = DistanceMetricType.L2,
                        type = VectorType.FLOAT32
                    )
                )
            ),
            alias = "VEC"
        )
        fields.append(fieldInfo)

        result = await search.create(glide_client, index3, fields, FtCreateOptions(DataType.HASH, prefixes))
        assert result == OK

        """
        Create a 6-dimensional JSON index using the HNSW algorithm
        FT.CREATE json_idx1 ON JSON PREFIX 1 json: SCHEMA $.vec AS VEC VECTOR HNSW 6 DIM 6 TYPE FLOAT32 DISTANCE_METRIC L2
        """
        index4 = "json_idx1"
        prefixes = []
        prefixes.append("json:")
        fields = []
        fieldInfo: FieldInfo = FieldInfo(
            identifier = "$.vec",
            fieldTypeInfo = FieldTypeInfo(
                FieldType.VECTOR,
                vectorTypeOptions = VectorTypeOptions(
                    VectorTypeAlgorithm.HNSW,
                    vectorTypeHnswAttributes = VectorTypeHnswAttributes(
                        dim = 6,
                        distanceMetric = DistanceMetricType.L2,
                        type = VectorType.FLOAT32
                    )
                )
            ),
            alias = "VEC"
        )
        fields.append(fieldInfo)

        result = await search.create(glide_client, index4, fields, FtCreateOptions(DataType.JSON, prefixes))
        assert result == OK

        """
        TO-DO:
        Add additional tests from VSS documentation that require a combination of commands to run.
        """
