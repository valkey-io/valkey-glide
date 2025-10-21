# Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

from typing import Optional

from .._glide_ffi import _GlideFFI
from ..logger import Level, Logger

ENCODING = "utf-8"

FINISHED_SCAN_CURSOR = "finished"


class ClusterScanCursor:
    """
    Represents a cursor for cluster scan operations.

    This class manages the state of a cluster scan cursor and automatically
    cleans up resources when the cursor is no longer needed.
    """

    def __init__(self, new_cursor: Optional[str] = None):
        """
        Create a new ClusterScanCursor instance.

        Args:
            new_cursor: Optional cursor string to initialize with. If None,
                       creates a cursor in the initial state.
        """
        _glide_ffi = _GlideFFI()
        self._ffi = _glide_ffi.ffi
        self._lib = _glide_ffi.lib

        self._cursor = new_cursor or "0"

    def get_cursor(self) -> str:
        """
        Get the current cursor string.

        Returns:
            str: The current cursor string
        """
        return self._cursor

    def is_finished(self) -> bool:
        """
        Check if the cluster scan is finished.

        Returns:
            bool: True if the scan is finished, False otherwise
        """
        return self._cursor == FINISHED_SCAN_CURSOR

    def __del__(self):
        """
        Clean up cluster scan cursor resources when the object is garbage collected.
        """
        try:
            if hasattr(self, "_cursor") and self._cursor:
                cursor_bytes = self._cursor.encode(ENCODING) + b"\0"
                cursor_buffer = self._ffi.from_buffer(cursor_bytes)

                self._lib.remove_cluster_scan_cursor(cursor_buffer)

        except Exception as e:
            Logger.log(
                Level.ERROR,
                "cluster_scan_cursor",
                f"Error during cluster scan cursor cleanup: {e}",
            )
