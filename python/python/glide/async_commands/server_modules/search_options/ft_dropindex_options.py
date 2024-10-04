from typing import List
from enum import Enum
from glide.async_commands.server_modules.search_constants import FtDropIndexKeywords

class DeleteDocument(Enum):
    """
    Options to either delete or not delete the document associated with the index deleted using [FT.DROPINDEX] command.
    """

    NOT_DELETE = 1
    """
    Not delete the document associated with the index. The default option.
    """
    DELETE = 2
    """
    Delete the document associated with the index.
    """

class FtDropIndexOptions:
    """
    This class represents the optional arguments for the [FT.DROPINDEX] command.
    """
    def __init__(
        self,
        deleteDocument: DeleteDocument = DeleteDocument.DELETE
    ):
        """
        Initialize the optional arguments for [FT.DROPINDEX] command.
        """
        self.deleteDocument = deleteDocument

    def getDropIndexOptions(self) -> List[str]:
        """
        Get the optional arguments for the [FT.DROPINDEX] command.

        Retuns:
            List[str]:
                List of optional arguments.
        """
        args = []
        if self.deleteDocument == DeleteDocument.DELETE:
            args.append(FtDropIndexKeywords.DELETE_DOCUMENT)
        return args    
