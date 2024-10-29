# Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0
import time
import uuid
from typing import List, Mapping, Union, cast

import pytest
from glide.async_commands.command_args import OrderBy
from glide.async_commands.server_modules import ft
from glide.async_commands.server_modules import json as GlideJson
from glide.async_commands.server_modules.ft_options.ft_aggregate_options import (
    FtAggregateApply,
    FtAggregateClause,
    FtAggregateGroupBy,
    FtAggregateOptions,
    FtAggregateReducer,
    FtAggregateSortBy,
    FtAggregateSortProperty,
)
from glide.async_commands.server_modules.ft_options.ft_create_options import (
    DataType,
    DistanceMetricType,
    Field,
    FtCreateOptions,
    NumericField,
    TagField,
    TextField,
    VectorAlgorithm,
    VectorField,
    VectorFieldAttributesHnsw,
    VectorType,
)
from glide.config import ProtocolVersion
from glide.constants import OK, TEncodable
from glide.exceptions import RequestError
from glide.glide_client import GlideClusterClient


@pytest.mark.asyncio
class TestFt:
    SearchResultField = Mapping[
        TEncodable, Union[TEncodable, Mapping[TEncodable, Union[TEncodable, int]]]
    ]

    SerchResultFieldsList = List[
        Mapping[
            TEncodable,
            Union[TEncodable, Mapping[TEncodable, Union[TEncodable, int]]],
        ]
    ]

    sleep_wait_time = 1  # This value is in seconds

    @pytest.mark.parametrize("cluster_mode", [True])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_ft_aliasadd(self, glide_client: GlideClusterClient):
        indexName: str = str(uuid.uuid4())
        alias: str = "alias"
        # Test ft.aliasadd throws an error if index does not exist.
        with pytest.raises(RequestError):
            await ft.aliasadd(glide_client, alias, indexName)

        # Test ft.aliasadd successfully adds an alias to an existing index.
        await TestFt._create_test_index_hash_type(self, glide_client, indexName)
        assert await ft.aliasadd(glide_client, alias, indexName) == OK
        assert await ft.dropindex(glide_client, indexName=indexName) == OK

        # Test ft.aliasadd for input of bytes type.
        indexNameString = str(uuid.uuid4())
        indexNameBytes = bytes(indexNameString, "utf-8")
        aliasNameBytes = b"alias-bytes"
        await TestFt._create_test_index_hash_type(self, glide_client, indexNameString)
        assert await ft.aliasadd(glide_client, aliasNameBytes, indexNameBytes) == OK
        assert await ft.dropindex(glide_client, indexName=indexNameString) == OK

    @pytest.mark.parametrize("cluster_mode", [True])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_ft_aliasdel(self, glide_client: GlideClusterClient):
        indexName: TEncodable = str(uuid.uuid4())
        alias: str = "alias"
        await TestFt._create_test_index_hash_type(self, glide_client, indexName)

        # Test if deleting a non existent alias throws an error.
        with pytest.raises(RequestError):
            await ft.aliasdel(glide_client, alias)

        # Test if an existing alias is deleted successfully.
        assert await ft.aliasadd(glide_client, alias, indexName) == OK
        assert await ft.aliasdel(glide_client, alias) == OK

        # Test if an existing alias is deleted successfully for bytes type input.
        assert await ft.aliasadd(glide_client, alias, indexName) == OK
        assert await ft.aliasdel(glide_client, bytes(alias, "utf-8")) == OK

        assert await ft.dropindex(glide_client, indexName=indexName) == OK

    @pytest.mark.parametrize("cluster_mode", [True])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_ft_aliasupdate(self, glide_client: GlideClusterClient):
        indexName: str = str(uuid.uuid4())
        alias: str = "alias"
        await TestFt._create_test_index_hash_type(self, glide_client, indexName)
        assert await ft.aliasadd(glide_client, alias, indexName) == OK
        newAliasName: str = "newAlias"
        newIndexName: str = str(uuid.uuid4())

        await TestFt._create_test_index_hash_type(self, glide_client, newIndexName)
        assert await ft.aliasadd(glide_client, newAliasName, newIndexName) == OK

        # Test if updating an already existing alias to point to an existing index returns "OK".
        assert await ft.aliasupdate(glide_client, newAliasName, indexName) == OK
        assert (
            await ft.aliasupdate(
                glide_client, bytes(alias, "utf-8"), bytes(newIndexName, "utf-8")
            )
            == OK
        )

        assert await ft.dropindex(glide_client, indexName=indexName) == OK
        assert await ft.dropindex(glide_client, indexName=newIndexName) == OK

    async def _create_test_index_hash_type(
        self, glide_client: GlideClusterClient, index_name: TEncodable
    ):
        # Helper function used for creating a basic index with hash data type with one text field.
        fields: List[Field] = []
        text_field_title: TextField = TextField("title")
        fields.append(text_field_title)

        prefix = "{hash-search-" + str(uuid.uuid4()) + "}:"
        prefixes: List[TEncodable] = []
        prefixes.append(prefix)

        result = await ft.create(
            glide_client, index_name, fields, FtCreateOptions(DataType.HASH, prefixes)
        )
        assert result == OK

    @pytest.mark.parametrize("cluster_mode", [True])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_ft_info(self, glide_client: GlideClusterClient):
        indexName = str(uuid.uuid4())
        await TestFt._create_test_index_with_vector_field(
            self, glide_client=glide_client, index_name=indexName
        )
        result = await ft.info(glide_client, indexName)
        assert await ft.dropindex(glide_client, indexName=indexName) == OK

        assert indexName.encode() == result.get(b"index_name")
        assert b"JSON" == result.get(b"key_type")
        assert [b"key-prefix"] == result.get(b"key_prefixes")

        # Get vector and text fields from the fields array.
        fields: TestFt.SerchResultFieldsList = cast(
            TestFt.SerchResultFieldsList, result.get(b"fields")
        )
        assert len(fields) == 2
        textField: TestFt.SearchResultField = {}
        vectorField: TestFt.SearchResultField = {}
        if fields[0].get(b"type") == b"VECTOR":
            vectorField = cast(TestFt.SearchResultField, fields[0])
            textField = cast(TestFt.SearchResultField, fields[1])
        else:
            vectorField = cast(TestFt.SearchResultField, fields[1])
            textField = cast(TestFt.SearchResultField, fields[0])

        # Compare vector field arguments
        assert b"$.vec" == vectorField.get(b"identifier")
        assert b"VECTOR" == vectorField.get(b"type")
        assert b"VEC" == vectorField.get(b"field_name")
        vectorFieldParams: Mapping[TEncodable, Union[TEncodable, int]] = cast(
            Mapping[TEncodable, Union[TEncodable, int]],
            vectorField.get(b"vector_params"),
        )
        assert DistanceMetricType.L2.value.encode() == vectorFieldParams.get(
            b"distance_metric"
        )
        assert 2 == vectorFieldParams.get(b"dimension")
        assert b"HNSW" == vectorFieldParams.get(b"algorithm")
        assert b"FLOAT32" == vectorFieldParams.get(b"data_type")

        # Compare text field arguments.
        assert b"$.text-field" == textField.get(b"identifier")
        assert b"TEXT" == textField.get(b"type")
        assert b"text-field" == textField.get(b"field_name")

        # Querying a missing index throws an error.
        with pytest.raises(RequestError):
            await ft.info(glide_client, str(uuid.uuid4()))

    async def _create_test_index_with_vector_field(
        self, glide_client: GlideClusterClient, index_name: TEncodable
    ):
        # Helper function used for creating an index with JSON data type with a text and vector field.
        fields: List[Field] = []
        textField: Field = TextField("$.text-field", "text-field")

        vectorFieldHash: VectorField = VectorField(
            name="$.vec",
            algorithm=VectorAlgorithm.HNSW,
            attributes=VectorFieldAttributesHnsw(
                dimensions=2,
                distance_metric=DistanceMetricType.L2,
                type=VectorType.FLOAT32,
            ),
            alias="VEC",
        )
        fields.append(vectorFieldHash)
        fields.append(textField)

        prefixes: List[TEncodable] = []
        prefixes.append("key-prefix")

        await ft.create(
            glide_client,
            indexName=index_name,
            schema=fields,
            options=FtCreateOptions(DataType.JSON, prefixes=prefixes),
        )

    @pytest.mark.parametrize("cluster_mode", [True])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_ft_explain(self, glide_client: GlideClusterClient):
        indexName = str(uuid.uuid4())
        await TestFt._create_test_index_for_ft_explain_commands(
            self=self, glide_client=glide_client, index_name=indexName
        )

        # FT.EXPLAIN on a search query containing numeric field.
        query = "@price:[0 10]"
        result = await ft.explain(glide_client, indexName=indexName, query=query)
        resultString = cast(bytes, result).decode(encoding="utf-8")
        assert "price" in resultString and "0" in resultString and "10" in resultString

        # FT.EXPLAIN on a search query containing numeric field and having bytes type input to the command.
        result = await ft.explain(
            glide_client, indexName=indexName.encode(), query=query.encode()
        )
        resultString = cast(bytes, result).decode(encoding="utf-8")
        assert "price" in resultString and "0" in resultString and "10" in resultString

        # FT.EXPLAIN on a search query that returns all data.
        result = await ft.explain(glide_client, indexName=indexName, query="*")
        resultString = cast(bytes, result).decode(encoding="utf-8")
        assert "*" in resultString

        assert await ft.dropindex(glide_client, indexName=indexName)

        # FT.EXPLAIN on a missing index throws an error.
        with pytest.raises(RequestError):
            await ft.explain(glide_client, str(uuid.uuid4()), "*")

    @pytest.mark.parametrize("cluster_mode", [True])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_ft_explaincli(self, glide_client: GlideClusterClient):
        indexName = str(uuid.uuid4())
        await TestFt._create_test_index_for_ft_explain_commands(
            self=self, glide_client=glide_client, index_name=indexName
        )

        # FT.EXPLAINCLI on a search query containing numeric field.
        query = "@price:[0 10]"
        result = await ft.explaincli(glide_client, indexName=indexName, query=query)
        resultStringArr = []
        for i in result:
            resultStringArr.append(cast(bytes, i).decode(encoding="utf-8").strip())
        assert (
            "price" in resultStringArr
            and "0" in resultStringArr
            and "10" in resultStringArr
        )

        # FT.EXPLAINCLI on a search query containing numeric field and having bytes type input to the command.
        result = await ft.explaincli(
            glide_client, indexName=indexName.encode(), query=query.encode()
        )
        resultStringArr = []
        for i in result:
            resultStringArr.append(cast(bytes, i).decode(encoding="utf-8").strip())
        assert (
            "price" in resultStringArr
            and "0" in resultStringArr
            and "10" in resultStringArr
        )

        # FT.EXPLAINCLI on a search query that returns all data.
        result = await ft.explaincli(glide_client, indexName=indexName, query="*")
        resultStringArr = []
        for i in result:
            resultStringArr.append(cast(bytes, i).decode(encoding="utf-8").strip())
        assert "*" in resultStringArr

        assert await ft.dropindex(glide_client, indexName=indexName)

        # FT.EXPLAINCLI on a missing index throws an error.
        with pytest.raises(RequestError):
            await ft.explaincli(glide_client, str(uuid.uuid4()), "*")

    async def _create_test_index_for_ft_explain_commands(
        self, glide_client: GlideClusterClient, index_name: TEncodable
    ):
        # Helper function used for creating an index having hash data type, one text field and one numeric field.
        fields: List[Field] = []
        numeric_field: NumericField = NumericField("price")
        text_field: TextField = TextField("title")
        fields.append(text_field)
        fields.append(numeric_field)

        prefix = "{hash-search-" + str(uuid.uuid4()) + "}:"
        prefixes: List[TEncodable] = []
        prefixes.append(prefix)

        assert (
            await ft.create(
                glide_client,
                index_name,
                fields,
                FtCreateOptions(DataType.HASH, prefixes),
            )
            == OK
        )

    @pytest.mark.parametrize("cluster_mode", [True])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_ft_aggregate_with_bicycles_data(
        self, glide_client: GlideClusterClient, protocol
    ):
        prefixBicycles = "{bicycles}:"
        indexBicycles = prefixBicycles + str(uuid.uuid4())
        await TestFt._create_index_for_ft_aggregate_with_bicycles_data(
            self=self,
            glide_client=glide_client,
            index_name=indexBicycles,
            prefix=prefixBicycles,
        )
        await TestFt._create_json_keys_for_ft_aggregate_with_bicycles_data(
            self=self, glide_client=glide_client, prefix=prefixBicycles
        )
        time.sleep(self.sleep_wait_time)

        # Run FT.AGGREGATE command with the following arguments: ['FT.AGGREGATE', '{bicycles}:1e15faab-a870-488e-b6cd-f2b76c6916a3', '*', 'LOAD', '1', '__key', 'GROUPBY', '1', '@condition', 'REDUCE', 'COUNT', '0', 'AS', 'bicycles']
        result = await ft.aggregate(
            glide_client,
            indexName=indexBicycles,
            query="*",
            options=FtAggregateOptions(
                loadFields=["__key"],
                clauses=[
                    FtAggregateGroupBy(
                        ["@condition"], [FtAggregateReducer("COUNT", [], "bicycles")]
                    )
                ],
            ),
        )
        assert await ft.dropindex(glide_client, indexName=indexBicycles) == OK
        sortedResult = sorted(result, key=lambda x: (x[b"condition"], x[b"bicycles"]))

        expectedResult = sorted(
            [
                {
                    b"condition": b"refurbished",
                    b"bicycles": b"1" if (protocol == ProtocolVersion.RESP2) else 1.0,
                },
                {
                    b"condition": b"new",
                    b"bicycles": b"5" if (protocol == ProtocolVersion.RESP2) else 5.0,
                },
                {
                    b"condition": b"used",
                    b"bicycles": b"4" if (protocol == ProtocolVersion.RESP2) else 4.0,
                },
            ],
            key=lambda x: (x[b"condition"], x[b"bicycles"]),
        )
        assert sortedResult == expectedResult

    @pytest.mark.parametrize("cluster_mode", [True])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_ft_aggregate_with_movies_data(
        self, glide_client: GlideClusterClient, protocol
    ):
        prefixMovies = "{movies}:"
        indexMovies = prefixMovies + str(uuid.uuid4())
        # Create index for movies data.
        await TestFt._create_index_for_ft_aggregate_with_movies_data(
            self=self,
            glide_client=glide_client,
            index_name=indexMovies,
            prefix=prefixMovies,
        )
        # Set JSON keys with movies data.
        await TestFt._create_hash_keys_for_ft_aggregate_with_movies_data(
            self=self, glide_client=glide_client, prefix=prefixMovies
        )
        # Wait for index to be updated.
        time.sleep(self.sleep_wait_time)

        # Run FT.AGGREGATE command with the following arguments:
        # ['FT.AGGREGATE', '{movies}:5a0e6257-3488-4514-96f2-f4c80f6cb0a9', '*', 'LOAD', '*', 'APPLY', 'ceil(@rating)', 'AS', 'r_rating', 'GROUPBY', '1', '@genre', 'REDUCE', 'COUNT', '0', 'AS', 'nb_of_movies', 'REDUCE', 'SUM', '1', 'votes', 'AS', 'nb_of_votes', 'REDUCE', 'AVG', '1', 'r_rating', 'AS', 'avg_rating', 'SORTBY', '4', '@avg_rating', 'DESC', '@nb_of_votes', 'DESC']

        result = await ft.aggregate(
            glide_client,
            indexName=indexMovies,
            query="*",
            options=FtAggregateOptions(
                loadAll=True,
                clauses=[
                    FtAggregateApply(expression="ceil(@rating)", name="r_rating"),
                    FtAggregateGroupBy(
                        ["@genre"],
                        [
                            FtAggregateReducer("COUNT", [], "nb_of_movies"),
                            FtAggregateReducer("SUM", ["votes"], "nb_of_votes"),
                            FtAggregateReducer("AVG", ["r_rating"], "avg_rating"),
                        ],
                    ),
                    FtAggregateSortBy(
                        properties=[
                            FtAggregateSortProperty("@avg_rating", OrderBy.DESC),
                            FtAggregateSortProperty("@nb_of_votes", OrderBy.DESC),
                        ]
                    ),
                ],
            ),
        )
        assert await ft.dropindex(glide_client, indexName=indexMovies) == OK
        sortedResult = sorted(
            result,
            key=lambda x: (
                x[b"genre"],
                x[b"nb_of_movies"],
                x[b"nb_of_votes"],
                x[b"avg_rating"],
            ),
        )
        expectedResultSet = sorted(
            [
                {
                    b"genre": b"Drama",
                    b"nb_of_movies": (
                        b"1" if (protocol == ProtocolVersion.RESP2) else 1.0
                    ),
                    b"nb_of_votes": (
                        b"1563839" if (protocol == ProtocolVersion.RESP2) else 1563839.0
                    ),
                    b"avg_rating": (
                        b"10" if (protocol == ProtocolVersion.RESP2) else 10.0
                    ),
                },
                {
                    b"genre": b"Action",
                    b"nb_of_movies": (
                        b"2" if (protocol == ProtocolVersion.RESP2) else 2.0
                    ),
                    b"nb_of_votes": (
                        b"2033895" if (protocol == ProtocolVersion.RESP2) else 2033895.0
                    ),
                    b"avg_rating": b"9" if (protocol == ProtocolVersion.RESP2) else 9.0,
                },
                {
                    b"genre": b"Thriller",
                    b"nb_of_movies": (
                        b"1" if (protocol == ProtocolVersion.RESP2) else 1.0
                    ),
                    b"nb_of_votes": (
                        b"559490" if (protocol == ProtocolVersion.RESP2) else 559490.0
                    ),
                    b"avg_rating": b"9" if (protocol == ProtocolVersion.RESP2) else 9.0,
                },
            ],
            key=lambda x: (
                x[b"genre"],
                x[b"nb_of_movies"],
                x[b"nb_of_votes"],
                x[b"avg_rating"],
            ),
        )
        assert expectedResultSet == sortedResult

    async def _create_index_for_ft_aggregate_with_bicycles_data(
        self, glide_client: GlideClusterClient, index_name: TEncodable, prefix
    ):
        fields: List[Field] = [
            TextField("$.model", "model"),
            TextField("$.description", "description"),
            NumericField("$.price", "price"),
            TagField("$.condition", "condition", ","),
        ]
        assert (
            await ft.create(
                glide_client,
                index_name,
                fields,
                FtCreateOptions(DataType.JSON, prefixes=[prefix]),
            )
            == OK
        )

    async def _create_json_keys_for_ft_aggregate_with_bicycles_data(
        self, glide_client: GlideClusterClient, prefix
    ):
        assert (
            await GlideJson.set(
                glide_client,
                prefix + "0",
                ".",
                '{"brand": "Velorim", "model": "Jigger", "price": 270, "description":'
                + ' "Small and powerful, the Jigger is the best ride for the smallest of tikes!'
                + " This is the tiniest kids\\u2019 pedal bike on the market available without a"
                + " coaster brake, the Jigger is the vehicle of choice for the rare tenacious"
                + ' little rider raring to go.", "condition": "new"}',
            )
            == OK
        )

        assert (
            await GlideJson.set(
                glide_client,
                prefix + "1",
                ".",
                '{"brand": "Bicyk", "model": "Hillcraft", "price": 1200, "description":'
                + ' "Kids want to ride with as little weight as possible. Especially on an'
                + ' incline! They may be at the age when a 27.5\\" wheel bike is just too clumsy'
                + ' coming off a 24\\" bike. The Hillcraft 26 is just the solution they need!",'
                + ' "condition": "used"}',
            )
            == OK
        )

        assert (
            await GlideJson.set(
                glide_client,
                prefix + "2",
                ".",
                '{"brand": "Nord", "model": "Chook air 5", "price": 815, "description":'
                + ' "The Chook Air 5  gives kids aged six years and older a durable and'
                + " uberlight mountain bike for their first experience on tracks and easy"
                + " cruising through forests and fields. The lower  top tube makes it easy to"
                + " mount and dismount in any situation, giving your kids greater safety on the"
                + ' trails.", "condition": "used"}',
            )
            == OK
        )

        assert (
            await GlideJson.set(
                glide_client,
                prefix + "3",
                ".",
                '{"brand": "Eva", "model": "Eva 291", "price": 3400, "description": "The'
                + " sister company to Nord, Eva launched in 2005 as the first and only"
                + " women-dedicated bicycle brand. Designed by women for women, allEva bikes are"
                + " optimized for the feminine physique using analytics from a body metrics"
                + " database. If you like 29ers, try the Eva 291. It\\u2019s a brand new bike for"
                + " 2022.. This full-suspension, cross-country ride has been designed for"
                + " velocity. The 291 has 100mm of front and rear travel, a superlight aluminum"
                + ' frame and fast-rolling 29-inch wheels. Yippee!", "condition": "used"}',
            )
            == OK
        )

        assert (
            await GlideJson.set(
                glide_client,
                prefix + "4",
                ".",
                '{"brand": "Noka Bikes", "model": "Kahuna", "price": 3200, "description":'
                + ' "Whether you want to try your hand at XC racing or are looking for a lively'
                + " trail bike that's just as inspiring on the climbs as it is over rougher"
                + " ground, the Wilder is one heck of a bike built specifically for short women."
                + " Both the frames and components have been tweaked to include a women\\u2019s"
                + ' saddle, different bars and unique colourway.", "condition": "used"}',
            )
            == OK
        )

        assert (
            await GlideJson.set(
                glide_client,
                prefix + "5",
                ".",
                '{"brand": "Breakout", "model": "XBN 2.1 Alloy", "price": 810,'
                + ' "description": "The XBN 2.1 Alloy is our entry-level road bike \\u2013 but'
                + " that\\u2019s not to say that it\\u2019s a basic machine. With an internal"
                + " weld aluminium frame, a full carbon fork, and the slick-shifting Claris gears"
                + " from Shimano\\u2019s, this is a bike which doesn\\u2019t break the bank and"
                + ' delivers craved performance.", "condition": "new"}',
            )
            == OK
        )

        assert (
            await GlideJson.set(
                glide_client,
                prefix + "6",
                ".",
                '{"brand": "ScramBikes", "model": "WattBike", "price": 2300,'
                + ' "description": "The WattBike is the best e-bike for people who still feel'
                + " young at heart. It has a Bafang 1000W mid-drive system and a 48V 17.5AH"
                + " Samsung Lithium-Ion battery, allowing you to ride for more than 60 miles on"
                + " one charge. It\\u2019s great for tackling hilly terrain or if you just fancy"
                + " a more leisurely ride. With three working modes, you can choose between"
                + ' E-bike, assisted bicycle, and normal bike modes.", "condition": "new"}',
            )
            == OK
        )

        assert (
            await GlideJson.set(
                glide_client,
                prefix + "7",
                ".",
                '{"brand": "Peaknetic", "model": "Secto", "price": 430, "description":'
                + ' "If you struggle with stiff fingers or a kinked neck or back after a few'
                " minutes on the road, this lightweight, aluminum bike alleviates those issues"
                " and allows you to enjoy the ride. From the ergonomic grips to the"
                " lumbar-supporting seat position, the Roll Low-Entry offers incredible"
                " comfort. The rear-inclined seat tube facilitates stability by allowing you to"
                " put a foot on the ground to balance at a stop, and the low step-over frame"
                " makes it accessible for all ability and mobility levels. The saddle is very"
                " soft, with a wide back to support your hip joints and a cutout in the center"
                " to redistribute that pressure. Rim brakes deliver satisfactory braking"
                " control, and the wide tires provide a smooth, stable ride on paved roads and"
                " gravel. Rack and fender mounts facilitate setting up the Roll Low-Entry as"
                " your preferred commuter, and the BMX-like handlebar offers space for mounting"
                ' a flashlight, bell, or phone holder.", "condition": "new"}',
            )
            == OK
        )

        assert (
            await GlideJson.set(
                glide_client,
                prefix + "8",
                ".",
                '{"brand": "nHill", "model": "Summit", "price": 1200, "description":'
                + ' "This budget mountain bike from nHill performs well both on bike paths and'
                + " on the trail. The fork with 100mm of travel absorbs rough terrain. Fat Kenda"
                + " Booster tires give you grip in corners and on wet trails. The Shimano Tourney"
                + " drivetrain offered enough gears for finding a comfortable pace to ride"
                + " uphill, and the Tektro hydraulic disc brakes break smoothly. Whether you want"
                + " an affordable bike that you can take to work, but also take trail in"
                + " mountains on the weekends or you\\u2019re just after a stable, comfortable"
                + ' ride for the bike path, the Summit gives a good value for money.",'
                + ' "condition": "new"}',
            )
            == OK
        )

        assert (
            await GlideJson.set(
                glide_client,
                prefix + "9",
                ".",
                '{"model": "ThrillCycle", "brand": "BikeShind", "price": 815,'
                + ' "description": "An artsy,  retro-inspired bicycle that\\u2019s as'
                + " functional as it is pretty: The ThrillCycle steel frame offers a smooth ride."
                + " A 9-speed drivetrain has enough gears for coasting in the city, but we"
                + " wouldn\\u2019t suggest taking it to the mountains. Fenders protect you from"
                + " mud, and a rear basket lets you transport groceries, flowers and books. The"
                + " ThrillCycle comes with a limited lifetime warranty, so this little guy will"
                + ' last you long past graduation.", "condition": "refurbished"}',
            )
            == OK
        )

    async def _create_index_for_ft_aggregate_with_movies_data(
        self, glide_client: GlideClusterClient, index_name: TEncodable, prefix
    ):
        fields: List[Field] = [
            TextField("title"),
            NumericField("release_year"),
            NumericField("rating"),
            TagField("genre"),
            NumericField("votes"),
        ]
        assert (
            await ft.create(
                glide_client,
                index_name,
                fields,
                FtCreateOptions(DataType.HASH, prefixes=[prefix]),
            )
            == OK
        )

    async def _create_hash_keys_for_ft_aggregate_with_movies_data(
        self, glide_client: GlideClusterClient, prefix
    ):
        await glide_client.hset(
            prefix + "11002",
            {
                "title": "Star Wars: Episode V - The Empire Strikes Back",
                "plot": "After the Rebels are brutally overpowered by the Empire on the ice planet Hoth,"
                + " Luke Skywalker begins Jedi training with Yoda, while his friends are"
                + " pursued by Darth Vader and a bounty hunter named Boba Fett all over the"
                + " galaxy.",
                "release_year": "1980",
                "genre": "Action",
                "rating": "8.7",
                "votes": "1127635",
                "imdb_id": "tt0080684",
            },
        )

        await glide_client.hset(
            prefix + "11003",
            {
                "title": "The Godfather",
                "plot": "The aging patriarch of an organized crime dynasty transfers control of his"
                + " clandestine empire to his reluctant son.",
                "release_year": "1972",
                "genre": "Drama",
                "rating": "9.2",
                "votes": "1563839",
                "imdb_id": "tt0068646",
            },
        )

        await glide_client.hset(
            prefix + "11004",
            {
                "title": "Heat",
                "plot": "A group of professional bank robbers start to feel the heat from police when they"
                + " unknowingly leave a clue at their latest heist.",
                "release_year": "1995",
                "genre": "Thriller",
                "rating": "8.2",
                "votes": "559490",
                "imdb_id": "tt0113277",
            },
        )

        await glide_client.hset(
            prefix + "11005",
            {
                "title": "Star Wars: Episode VI - Return of the Jedi",
                "plot": "The Rebels dispatch to Endor to destroy the second Empire's Death Star.",
                "release_year": "1983",
                "genre": "Action",
                "rating": "8.3",
                "votes": "906260",
                "imdb_id": "tt0086190",
            },
        )
