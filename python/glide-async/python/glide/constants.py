# Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

import warnings

# Issue deprecation warning for direct module imports
warnings.warn(
    "Importing from 'glide.constants' is deprecated. Import from glide instead. For example: 'from glide import OK'",
    DeprecationWarning,
    stacklevel=2,
)

# Re-export all constants from the new location
from glide_shared.constants import (
    OK,
    TOK,
    FtAggregateResponse,
    FtInfoResponse,
    FtProfileResponse,
    FtSearchResponse,
    TClusterResponse,
    TEncodable,
    TFunctionListResponse,
    TFunctionStatsFullResponse,
    TFunctionStatsSingleNodeResponse,
    TJsonResponse,
    TJsonUniversalResponse,
    TResult,
    TSingleNodeRoute,
    TXInfoStreamFullResponse,
    TXInfoStreamResponse,
)

__all__ = [
    "OK",
    "TOK",
    "FtAggregateResponse",
    "FtInfoResponse",
    "FtProfileResponse",
    "FtSearchResponse",
    "TClusterResponse",
    "TEncodable",
    "TFunctionListResponse",
    "TFunctionStatsFullResponse",
    "TFunctionStatsSingleNodeResponse",
    "TJsonResponse",
    "TJsonUniversalResponse",
    "TResult",
    "TSingleNodeRoute",
    "TXInfoStreamFullResponse",
    "TXInfoStreamResponse",
]
