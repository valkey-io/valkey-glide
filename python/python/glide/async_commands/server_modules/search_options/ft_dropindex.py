from typing import List
from glide.async_commands.server_modules.search_constants import FtDropIndexKeywords
class DeleteDocument:
    NOT_DELETE = 1
    DELETE = 2


class FtDropIndexOptions:
    def __init__(
        self,
        deleteDocument: DeleteDocument = DeleteDocument.DELETE
    ):
        self.deleteDocument = deleteDocument
    def getDropIndexOptions(self) -> List[str]:
        args = []
        if self.deleteDocument == DeleteDocument.DELETE:
            args.append(FtDropIndexKeywords.DELETE_DOCUMENT)

        return args    
