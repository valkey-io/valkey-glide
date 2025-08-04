# Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

from typing import List, Mapping, Optional

from glide_shared.commands.server_modules.ft_options.ft_constants import (
    FtSearchKeywords,
)
from glide_shared.constants import TEncodable


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
    ):
        """
        Initialize the FT.SEARCH optional fields.

        Args:
            return_fields (Optional[List[ReturnField]]): The fields of a key that are returned by FT.SEARCH command.
                See `ReturnField`.
            timeout (Optional[int]): This value overrides the timeout parameter of the module.
                The unit for the timout is in milliseconds.
            params (Optional[Mapping[TEncodable, TEncodable]]): Param key/value pairs that can be referenced from within the
                query expression.
            limit (Optional[FtSearchLimit]): This option provides pagination capability. Only the keys that satisfy the offset
                and count values are returned. See `FtSearchLimit`.
            count (Optional[bool]): This flag option suppresses returning the contents of keys.
                Only the number of keys is returned.
        """
        self.return_fields = return_fields
        self.timeout = timeout
        self.params = params
        self.limit = limit
        self.count = count

    def to_args(self) -> List[TEncodable]:
        """
        Get the optional arguments for the FT.SEARCH command.

        Returns:
            List[TEncodable]:
                List of FT.SEARCH optional agruments.
        """
        args: List[TEncodable] = []
        if self.return_fields:
            args.append(FtSearchKeywords.RETURN)
            return_field_args: List[TEncodable] = []
            for return_field in self.return_fields:
                return_field_args.extend(return_field.to_args())
            args.append(str(len(return_field_args)))
            args.extend(return_field_args)
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
        return args
