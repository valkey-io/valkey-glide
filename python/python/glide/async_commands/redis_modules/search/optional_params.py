from enum import Enum


class FieldFlags(Enum):
    """
    - USE_FIELDS - store attribute bits for each term.
    - NO_FIELDS - does not store attribute bits for each term. Saves memory but does not allow filtering
            by specific attributes.
    """

    USE_FIELDS = False
    NO_FIELDS = True


class Frequencies(Enum):
    """
    - SAVE_FREQUENCIES - saves the term frequencies in the index.
    - NO_FREQUENCIES - avoids saving the term frequencies in the index. Saves memory but does not allow
        sorting based on the frequencies of a given term within the document.
    """

    SAVE_FREQUENCIES = False
    NO_FREQUENCIES = True


class Highlights(Enum):
    """
    - USE_HIGHLIGHTS - stores corresponding byte offsets for term positions.
    - NO_HIGHLIGHTS - conserves storage space and memory by disabling highlighting support.
            Corresponding byte offsets for term positions are not stored.
            Implied by NOOFFSETS (Offset.NO_OFFSET).
    """

    USE_HIGHLIGHTS = False
    NO_HIGHLIGHTS = True


class InitialScan(Enum):
    """
    - SCAN_INDEX - scan the index.
    - SKIP_SCAN - does not scan the index.
    """

    SCAN_INDEX = False
    SKIP_SCAN = True


class Offset(Enum):
    """
    - STORE_OFFSET - store term offsets for documents.
    - NO_OFFSET - does not store term offsets for documents. Saves memory but does not allow exact searches or highlighting.
            Implies NOHL (Highlights.NO_HIGHLIGHTS).
    """

    STORE_OFFSET = False
    NO_OFFSET = True
