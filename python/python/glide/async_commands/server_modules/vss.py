from typing import List, Optional, cast
from glide.glide_client import TGlideClient
from glide.constants import TEncodable
from glide.protobuf.command_request_pb2 import RequestType

class CreateOptions:
    def __init__(
        self,
        optionName: str,
        optionValue: Optional[str] = None
    ):
        self.optionName = optionName
        self.optionValue = optionValue

    def getCreateOptions(self) -> str:
        args = []
        if self.optionName:
            args.extend(self.optionName)
        if self.optionValue:
            args.extend(self.optionValue)


async def create(
    client: TGlideClient,
    indexName: TEncodable,
    options: List[CreateOptions]
):
    args: List[TEncodable] = [indexName]
    for createOption in options:
        args.extend(createOption.getCreateOptions())
    return cast(Optional[bytes], await client._execute_command(RequestType.FtCreate, args))


async def info(
    client: TGlideClient,
    indexName: TEncodable,
):
    args: List[TEncodable] = [indexName]
    return cast(Optional[bytes], await client._execute_command(RequestType.FtInfo, args))
