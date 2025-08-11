# Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

from __future__ import annotations

import traceback
from enum import Enum
from pathlib import Path
from typing import Optional, cast

from glide_shared.exceptions import LoggerError

from ._glide_ffi import _GlideFFI

ENCODING = "utf-8"
CURR_DIR = Path(__file__).resolve().parent
LIB_FILE = CURR_DIR / "libglide_ffi.so"


class Level(Enum):
    ERROR = 0
    WARN = 1
    INFO = 2
    DEBUG = 3
    TRACE = 4
    OFF = 5


class Logger:
    """
    A singleton class that allows logging which is consistent with logs from the internal GLIDE core.
    The logger can be set up in 2 ways -
    1. By calling Logger.init, which configures the logger only if it wasn't previously configured.
    2. By calling Logger.set_logger_config, which replaces the existing configuration, and means that new logs will not be
    saved with the logs that were sent before the call.
    If none of these functions are called, the first log attempt will initialize a new logger with default configuration.
    """

    _instance: Logger | None = None
    _glide_ffi = _GlideFFI()
    _ffi = _glide_ffi.ffi
    _lib = _glide_ffi.lib
    logger_level: Level = Level.OFF

    def __init__(self, level: Optional[Level] = None, file_name: Optional[str] = None):
        c_level = (
            Logger._ffi.new("Level*", level.value)
            if level is not None
            else Logger._ffi.NULL
        )
        c_file_name = (
            Logger._ffi.new("char[]", file_name.encode(ENCODING))
            if file_name
            else Logger._ffi.NULL
        )

        result_ptr = Logger._lib.init(c_level, c_file_name)

        if result_ptr != Logger._ffi.NULL:
            try:
                if result_ptr.log_error != Logger._ffi.NULL:
                    error_str = cast(
                        bytes, Logger._ffi.string(result_ptr.log_error)
                    ).decode(ENCODING)
                    raise LoggerError(f"Logger initialization failed: {error_str}")
                else:
                    Logger.logger_level = Level(result_ptr.level)

            finally:
                Logger._lib.free_log_result(result_ptr)
        else:
            raise LoggerError("Logger init received a null pointer")

    @classmethod
    def init(cls, level: Optional[Level] = None, file_name: Optional[str] = None):
        """
        Initialize a logger if it wasn't initialized before - this method is meant to be used when there is no intention to
        replace an existing logger. Otherwise, use `set_logger_config` for overriding the existing logger configs.
        The logger will filter all logs with a level lower than the given level.
        If given a file_name argument, will write the logs to files postfixed with file_name. If file_name isn't provided,
        the logs will be written to the console.

        Args:
            level (Optional[Level]): Set the logger level to one of [ERROR, WARN, INFO, DEBUG, TRACE, OFF].
                If log level isn't provided, the logger will be configured with default configuration.
                To turn off logging completely, set the level to Level.OFF.
            file_name (Optional[str]): If provided the target of the logs will be the file mentioned.
                Otherwise, logs will be printed to the console.
        """
        if cls._instance is None:
            cls._instance = cls(level, file_name)

    @classmethod
    def log(
        cls,
        log_level: Level,
        log_identifier: str,
        message: str,
        err: Optional[Exception] = None,
    ):
        """
        Logs the provided message if the provided log level is lower then the logger level.

        Args:
            log_level (Level): The log level of the provided message.
            log_identifier (str): The log identifier should give the log a context.
            message (str): The message to log.
            err (Optional[Exception]): The exception or error to log.
        """
        if not cls._instance:
            cls._instance = cls(None)
        if log_level.value > Logger.logger_level.value:
            return
        if err:
            message = f"{message}: {traceback.format_exception(err)}"
        c_identifier = Logger._ffi.new("char[]", log_identifier.encode(ENCODING))
        c_message = Logger._ffi.new("char[]", message.encode(ENCODING))
        result_ptr = Logger._lib.glide_log(log_level.value, c_identifier, c_message)

        if result_ptr != Logger._ffi.NULL:
            try:
                if result_ptr.log_error != Logger._ffi.NULL:
                    error_str = cast(
                        bytes, Logger._ffi.string(result_ptr.log_error)
                    ).decode(ENCODING)

                    # If the log failed due to invalid provided identifier or message,
                    # Log the FFI log error using logger_core directly
                    error_identifier = Logger._ffi.new(
                        "char[]", "Logger".encode(ENCODING)
                    )
                    error_message = Logger._ffi.new(
                        "char[]", f"log error: {error_str}".encode(ENCODING)
                    )
                    error_result_ptr = Logger._lib.glide_log(
                        Level.ERROR.value, error_identifier, error_message
                    )
                    if error_result_ptr != Logger._ffi.NULL:
                        Logger._lib.free_log_result(error_result_ptr)

            finally:
                Logger._lib.free_log_result(result_ptr)

        else:
            # Log the null pointer error using logger_core directly
            error_identifier = Logger._ffi.new("char[]", "Logger".encode(ENCODING))
            error_message = Logger._ffi.new(
                "char[]", "Log function returned a null pointer".encode(ENCODING)
            )
            error_result_ptr = Logger._lib.glide_log(
                Level.ERROR.value, error_identifier, error_message
            )
            if error_result_ptr != Logger._ffi.NULL:
                Logger._lib.free_log_result(error_result_ptr)

    @classmethod
    def set_logger_config(
        cls, level: Optional[Level] = None, file_name: Optional[str] = None
    ):
        """
        Creates a new logger instance and configure it with the provided log level and file name.

        Args:
            level (Optional[Level]): Set the logger level to one of [ERROR, WARN, INFO, DEBUG, TRACE, OFF].
                If log level isn't provided, the logger will be configured with default configuration.
                To turn off logging completely, set the level to OFF.
            file_name (Optional[str]): If provided the target of the logs will be the file mentioned.
                Otherwise, logs will be printed to the console.
        """
        Logger._instance = cls(level, file_name)
