class DeleteDocument:
    NOT_DELETE_DOCUMENT = 1
    DELETE_DOCUMENT = 2


class FtDropIndexOptions:
    def __init__(
        self,
        deleteDocument: DeleteDocument = DeleteDocument.NOT_DELETE_DOCUMENT
    ):
        self.deleteDocument = deleteDocument
