# Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

from datetime import datetime, timedelta, timezone
from typing import Mapping

import pytest
from glide_shared.commands.batch import Batch
from glide_shared.commands.core_options import (
    ConditionalChange,
    ExpiryGetEx,
    ExpirySet,
    ExpiryType,
    ExpiryTypeGetEx,
    HashFieldConditionalChange,
    MigrateOptions,
    _build_hsetex_args,
)
from glide_shared.constants import TEncodable
from glide_shared.protobuf.command_request_pb2 import RequestType

from tests.utils.utils import is_single_response


class TestCommandsUnitTests:
    def test_expiry_cmd_args(self):
        exp_sec = ExpirySet(ExpiryType.SEC, 5)
        assert exp_sec.get_cmd_args() == ["EX", "5"]

        exp_sec_timedelta = ExpirySet(ExpiryType.SEC, timedelta(seconds=5))
        assert exp_sec_timedelta.get_cmd_args() == ["EX", "5"]

        exp_millsec = ExpirySet(ExpiryType.MILLSEC, 5)
        assert exp_millsec.get_cmd_args() == ["PX", "5"]

        exp_millsec_timedelta = ExpirySet(ExpiryType.MILLSEC, timedelta(seconds=5))
        assert exp_millsec_timedelta.get_cmd_args() == ["PX", "5000"]

        exp_millsec_timedelta = ExpirySet(ExpiryType.MILLSEC, timedelta(seconds=5))
        assert exp_millsec_timedelta.get_cmd_args() == ["PX", "5000"]

        exp_unix_sec = ExpirySet(ExpiryType.UNIX_SEC, 1682575739)
        assert exp_unix_sec.get_cmd_args() == ["EXAT", "1682575739"]

        exp_unix_sec_datetime = ExpirySet(
            ExpiryType.UNIX_SEC,
            datetime(2023, 4, 27, 23, 55, 59, 342380, timezone.utc),
        )
        assert exp_unix_sec_datetime.get_cmd_args() == ["EXAT", "1682639759"]

        exp_unix_millisec = ExpirySet(ExpiryType.UNIX_MILLSEC, 1682586559964)
        assert exp_unix_millisec.get_cmd_args() == ["PXAT", "1682586559964"]

        exp_unix_millisec_datetime = ExpirySet(
            ExpiryType.UNIX_MILLSEC,
            datetime(2023, 4, 27, 23, 55, 59, 342380, timezone.utc),
        )
        assert exp_unix_millisec_datetime.get_cmd_args() == ["PXAT", "1682639759342"]

    def test_get_expiry_cmd_args(self):
        exp_sec = ExpiryGetEx(ExpiryTypeGetEx.SEC, 5)
        assert exp_sec.get_cmd_args() == ["EX", "5"]

        exp_sec_timedelta = ExpiryGetEx(ExpiryTypeGetEx.SEC, timedelta(seconds=5))
        assert exp_sec_timedelta.get_cmd_args() == ["EX", "5"]

        exp_millsec = ExpiryGetEx(ExpiryTypeGetEx.MILLSEC, 5)
        assert exp_millsec.get_cmd_args() == ["PX", "5"]

        exp_millsec_timedelta = ExpiryGetEx(
            ExpiryTypeGetEx.MILLSEC, timedelta(seconds=5)
        )
        assert exp_millsec_timedelta.get_cmd_args() == ["PX", "5000"]

        exp_millsec_timedelta = ExpiryGetEx(
            ExpiryTypeGetEx.MILLSEC, timedelta(seconds=5)
        )
        assert exp_millsec_timedelta.get_cmd_args() == ["PX", "5000"]

        exp_unix_sec = ExpiryGetEx(ExpiryTypeGetEx.UNIX_SEC, 1682575739)
        assert exp_unix_sec.get_cmd_args() == ["EXAT", "1682575739"]

        exp_unix_sec_datetime = ExpiryGetEx(
            ExpiryTypeGetEx.UNIX_SEC,
            datetime(2023, 4, 27, 23, 55, 59, 342380, timezone.utc),
        )
        assert exp_unix_sec_datetime.get_cmd_args() == ["EXAT", "1682639759"]

        exp_unix_millisec = ExpiryGetEx(ExpiryTypeGetEx.UNIX_MILLSEC, 1682586559964)
        assert exp_unix_millisec.get_cmd_args() == ["PXAT", "1682586559964"]

        exp_unix_millisec_datetime = ExpiryGetEx(
            ExpiryTypeGetEx.UNIX_MILLSEC,
            datetime(2023, 4, 27, 23, 55, 59, 342380, timezone.utc),
        )
        assert exp_unix_millisec_datetime.get_cmd_args() == ["PXAT", "1682639759342"]

        exp_persist = ExpiryGetEx(
            ExpiryTypeGetEx.PERSIST,
            None,
        )
        assert exp_persist.get_cmd_args() == ["PERSIST"]

    def test_expiry_raises_on_value_error(self):
        with pytest.raises(ValueError):
            ExpirySet(ExpiryType.SEC, 5.5)

    def test_expiry_equality(self):
        assert ExpirySet(ExpiryType.SEC, 2) == ExpirySet(ExpiryType.SEC, 2)
        assert ExpirySet(
            ExpiryType.UNIX_SEC,
            datetime(2023, 4, 27, 23, 55, 59, 342380, timezone.utc),
        ) == ExpirySet(
            ExpiryType.UNIX_SEC,
            datetime(2023, 4, 27, 23, 55, 59, 342380, timezone.utc),
        )

        assert not ExpirySet(ExpiryType.SEC, 1) == 1

    def test_is_single_response(self):
        assert is_single_response("This is a string value", "")
        assert is_single_response(["value", "value"], [""])
        assert not is_single_response(
            [["value", ["value"]], ["value", ["valued"]]], [""]
        )
        assert is_single_response(None, None)


class TestMigrateOptions:
    def test_no_options(self):
        assert MigrateOptions().to_args() == []

    def test_copy(self):
        assert MigrateOptions(copy=True).to_args() == ["COPY"]

    def test_replace(self):
        assert MigrateOptions(replace=True).to_args() == ["REPLACE"]

    def test_copy_and_replace(self):
        assert MigrateOptions(copy=True, replace=True).to_args() == ["COPY", "REPLACE"]

    def test_password(self):
        assert MigrateOptions(password="secret").to_args() == ["AUTH", "secret"]

    def test_username_and_password(self):
        assert MigrateOptions(username="user", password="secret").to_args() == [
            "AUTH2",
            "user",
            "secret",
        ]

    def test_all_options(self):
        assert MigrateOptions(
            copy=True, replace=True, username="user", password="secret"
        ).to_args() == ["COPY", "REPLACE", "AUTH2", "user", "secret"]


NX = ConditionalChange.ONLY_IF_DOES_NOT_EXIST
XX = ConditionalChange.ONLY_IF_EXISTS
FNX = HashFieldConditionalChange.ONLY_IF_NONE_EXIST
FXX = HashFieldConditionalChange.ONLY_IF_ALL_EXIST
EX_60 = ExpirySet(ExpiryType.SEC, 60)

HSETEX_FIELDS: Mapping[TEncodable, TEncodable] = {"field1": "value1"}
HSETEX_FIELDS_ARGS = ["FIELDS", "1", "field1", "value1"]

# Expected command arguments follow the HSETEX order: [NX | XX] [FNX | FXX] [expiry].
HSETEX_KEY_CONDITIONS = [(None, []), (NX, ["NX"]), (XX, ["XX"])]
HSETEX_FIELD_CONDITIONS = [(None, []), (FNX, ["FNX"]), (FXX, ["FXX"])]
HSETEX_EXPIRIES = [(None, []), (EX_60, ["EX", "60"])]


class TestHSetExArgs:
    @pytest.mark.parametrize("key_condition, key_args", HSETEX_KEY_CONDITIONS)
    @pytest.mark.parametrize("field_condition, field_args", HSETEX_FIELD_CONDITIONS)
    @pytest.mark.parametrize("expiry, expiry_args", HSETEX_EXPIRIES)
    def test_hsetex_args(
        self,
        key_condition,
        key_args,
        field_condition,
        field_args,
        expiry,
        expiry_args,
    ):
        assert _build_hsetex_args(
            "key",
            HSETEX_FIELDS,
            key_conditional_change=key_condition,
            field_conditional_change=field_condition,
            expiry=expiry,
        ) == ["key", *key_args, *field_args, *expiry_args, *HSETEX_FIELDS_ARGS]

    def test_batch_builds_hsetex_command(self):
        batch = Batch(is_atomic=False).hsetex(
            "key",
            {"field1": "value1", "field2": "value2"},
            field_conditional_change=FNX,
            expiry=EX_60,
            key_conditional_change=XX,
        )
        assert batch.commands == [
            (
                RequestType.HSetEx,
                [
                    "key",
                    "XX",
                    "FNX",
                    "EX",
                    "60",
                    "FIELDS",
                    "2",
                    "field1",
                    "value1",
                    "field2",
                    "value2",
                ],
            )
        ]
