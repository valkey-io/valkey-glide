# Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

from enum import Enum
from typing import List, Mapping, Optional

from glide_shared.commands.server_modules.ft_options.ft_constants import (
    FtSearchKeywords,
)
from glide_shared.constants import TEncodable


class OrderBy(Enum):
    """Sort order for FT.SEARCH SORTBY clause."""

    ASC = "ASC"
    DESC = "DESC"


class InfoScope(Enum):
    """Controls which nodes provide index information in cluster mode."""

    LOCAL = "LOCAL"
    """Only the executing (local) node provides index information. This is the default."""
    PRIMARY = "PRIMARY"
    """Primary nodes of every shard are queried. Only valid in cluster mode."""
    CLUSTER = "CLUSTER"
    """All nodes (primary and replica) are queried. Only valid in cluster mode."""


class ShardScope(Enum):
    """Shard scope for cluster query operations."""

    ALLSHARDS = "ALLSHARDS"
    """Terminate with timeout error if not all shards respond. This is the default."""
    SOMESHARDS = "SOMESHARDS"
    """Generate a best-effort reply if not all shards respond within the timeout."""


class ConsistencyMode(Enum):
    """Consistency mode for cluster query operations."""

    CONSISTENT = "CONSISTENT"
    """Terminate with an error if the cluster is in an inconsistent state. This is the default."""
    INCONSISTENT = "INCONSISTENT"
    """Generate a best-effort reply if the cluster remains inconsistent within the timeout."""


class FtSearchLimit:
    """
    This class represents the arguments for the LIMIT option of the FT.SEARCH command.
    """

    def __init__(self, offset: int, count: int):
        """
        Initialize a new FtSearchLimit instance.

        Args:
            offset (int): The number of keys to skip before returning the result for the FT.SEARCH command.
            count (int): The total number of keys to be returned by FT.SEARCH command.
        """
        self.offset = offset
        self.count = count

    def to_args(self) -> List[TEncodable]:
        """
        Get the arguments for the LIMIT option of FT.SEARCH.

        Returns:
            List[TEncodable]: A list of LIMIT option arguments.
        """
        args: List[TEncodable] = [
            FtSearchKeywords.LIMIT,
            str(self.offset),
            str(self.count),
        ]
        return args


class ReturnField:
    """
    This class represents the arguments for the RETURN option of the FT.SEARCH command.
    """

    def __init__(
        self, field_identifier: TEncodable, alias: Optional[TEncodable] = None
    ):
        """
        Initialize a new ReturnField instance.

        Args:
            field_identifier (TEncodable): The identifier for the field of the key that has to returned as a result of
                FT.SEARCH command.
            alias (Optional[TEncodable]): The alias to override the name of the field in the FT.SEARCH result.
        """
        self.field_identifier = field_identifier
        self.alias = alias

    def to_args(self) -> List[TEncodable]:
        """
        Get the arguments for the RETURN option of FT.SEARCH.

        Returns:
            List[TEncodable]: A list of RETURN option arguments.
        """
        args: List[TEncodable] = [self.field_identifier]
        if self.alias:
            args.append(FtSearchKeywords.AS)
            args.append(self.alias)
        return args


class FtSearchOptions:
    """
    This class represents the input options to be used in the FT.SEARCH command.
    All fields in this class are optional inputs for FT.SEARCH.
    """

    def __init__(
        self,
        return_fields: Optional[List[ReturnField]] = None,
        timeout: Optional[int] = None,
        params: Optional[Mapping[TEncodable, TEncodable]] = None,
        limit: Optional[FtSearchLimit] = None,
        count: Optional[bool] = False,
        nocontent: Optional[bool] = False,
        dialect: Optional[int] = None,
        verbatim: Optional[bool] = False,
        inorder: Optional[bool] = False,
        slop: Optional[int] = None,
        sortby: Optional[TEncodable] = None,
        sortby_order: Optional[OrderBy] = None,
        withsortkeys: Optional[bool] = False,
        shard_scope: Optional[ShardScope] = None,
        consistency: Optional[ConsistencyMode] = None,
    ):
        """
        Initialize the FT.SEARCH optional fields.

        Args:
            return_fields (Optional[List[ReturnField]]): The fields of a key that are returned by FT.SEARCH command.
                See `ReturnField`.
            timeout (Optional[int]): This value overrides the timeout parameter of the module.
                The unit for the timeout is in milliseconds.
            params (Optional[Mapping[TEncodable, TEncodable]]): Param key/value pairs that can be referenced from within the
                query expression.
            limit (Optional[FtSearchLimit]): This option provides pagination capability. Only the keys that satisfy the offset
                and count values are returned. See `FtSearchLimit`.
            count (Optional[bool]): This flag option suppresses returning the contents of keys.
                Only the number of keys is returned.
            nocontent (Optional[bool]): If set, the query returns only the document IDs and not the content.
                The document entries in the result will have empty attribute maps.
            dialect (Optional[int]): The query dialect version to use. The only supported dialect is ``2``.
            verbatim (Optional[bool]): If set, stemming is not applied to text terms in the query.
            inorder (Optional[bool]): If set, proximity matching of text terms must be in order.
            slop (Optional[int]): Specifies a slop value for proximity matching of text terms.
            sortby (Optional[TEncodable]): Field name to sort results by. Sorting is applied before the LIMIT clause.
            sortby_order (Optional[OrderBy]): Sort direction (ASC or DESC). Only used when ``sortby`` is set.
            withsortkeys (Optional[bool]): If set and ``sortby`` is specified, augments the output
                with the sort key value. When enabled, each document value in the result map becomes
                a two-element list ``[sort_key, field_map]`` instead of just ``field_map``. The sort
                key is the value of the field used for sorting, or ``None`` if the field is missing.
            shard_scope (Optional[ShardScope]): Controls shard participation in cluster mode. See `ShardScope`.
            consistency (Optional[ConsistencyMode]): Controls consistency requirements in cluster mode. See `ConsistencyMode`.
        """
        self.return_fields = return_fields
        self.timeout = timeout
        self.params = params
        self.limit = limit
        self.count = count
        self.nocontent = nocontent
        self.dialect = dialect
        self.verbatim = verbatim
        self.inorder = inorder
        self.slop = slop
        self.sortby = sortby
        self.sortby_order = sortby_order
        self.withsortkeys = withsortkeys
        self.shard_scope = shard_scope
        self.consistency = consistency

    def _validate(self) -> None:
        """Validate mutually dependent options."""
        if self.sortby is None and self.sortby_order is not None:
            raise ValueError("sortby_order requires sortby to be set.")
        if self.sortby is None and self.withsortkeys:
            raise ValueError("withsortkeys requires sortby to be set.")

    def _query_flags_to_args(self) -> List[TEncodable]:
        """Serialize cluster-mode and query-parsing flags."""
        args: List[TEncodable] = []
        if self.shard_scope:
            args.append(self.shard_scope.value)
        if self.consistency:
            args.append(self.consistency.value)
        if self.nocontent:
            args.append(FtSearchKeywords.NOCONTENT)
        if self.verbatim:
            args.append(FtSearchKeywords.VERBATIM)
        if self.inorder:
            args.append(FtSearchKeywords.INORDER)
        if self.slop is not None:
            args.extend([FtSearchKeywords.SLOP, str(self.slop)])
        return args

    def to_args(self) -> List[TEncodable]:
        """
        Get the optional arguments for the FT.SEARCH command.

        Returns:
            List[TEncodable]:
                List of FT.SEARCH optional arguments.
        """
        self._validate()
        args: List[TEncodable] = self._query_flags_to_args()
        if self.return_fields:
            args.append(FtSearchKeywords.RETURN)
            return_field_args: List[TEncodable] = []
            for return_field in self.return_fields:
                return_field_args.extend(return_field.to_args())
            args.append(str(len(return_field_args)))
            args.extend(return_field_args)
        if self.sortby is not None:
            args.append(FtSearchKeywords.SORTBY)
            args.append(self.sortby)
            if self.sortby_order:
                args.append(self.sortby_order.value)
        if self.withsortkeys:
            args.append(FtSearchKeywords.WITHSORTKEYS)
        if self.timeout:
            args.append(FtSearchKeywords.TIMEOUT)
            args.append(str(self.timeout))
        if self.params:
            args.append(FtSearchKeywords.PARAMS)
            args.append(str(len(self.params) * 2))
            for name, value in self.params.items():
                args.append(name)
                args.append(value)
        if self.limit:
            args.extend(self.limit.to_args())
        if self.count:
            args.append(FtSearchKeywords.COUNT)
        if self.dialect is not None:
            args.extend([FtSearchKeywords.DIALECT, str(self.dialect)])
        return args
