# Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

from __future__ import annotations

import traceback
from enum import Enum
from typing import Any, Callable, Optional

from cffi import FFI

from glide.shared.constants import LIB_FILE

ENCODING = "utf-8"


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

    _instance = None
    logger_level: Level
    _is_ffi: bool
    log_function: Optional[Callable]
    _ffi: FFI
    _lib: Any

    def __init__(self, level: Optional[Level] = None, file_name: Optional[str] = None):
        try:
            from .glide import py_init, py_log

            Logger._is_ffi = False
            Logger.log_function = py_log

            pyo3_level = self._python_to_pyo3_level(level)
            logger_level = py_init(pyo3_level, file_name)
            Logger.logger_level = Level(level)

        except Exception as e:
            if isinstance(e, ImportError):
                Logger._is_ffi = True
                self._init_ffi()

                ffi_level = (
                    self._ffi.new("Level*", level.value)
                    if level is not None
                    else self._ffi.NULL
                )
                c_file_name = (
                    self._ffi.new("char[]", file_name.encode(ENCODING))
                    if file_name
                    else self._ffi.NULL
                )

                if file_name is not None:
                    c_file_name = self._ffi.new("char[]", file_name.encode(ENCODING))
                else:
                    c_file_name = self._ffi.NULL

                logger_level = self._lib.init(ffi_level, c_file_name)
                Logger.logger_level = Level(logger_level)

            else:
                raise e

    @classmethod
    def init(cls, level: Optional[Level] = None, file_name: Optional[str] = None):
        """
        Initialize a logger if it wasn't initialized before - this method is meant to be used when there is no intention to
        replace an existing logger.
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
        if err:
            message = f"{message}: {traceback.format_exception(err)}"

        if not cls._is_ffi:
            logger_level = cls._python_to_pyo3_level(Logger.logger_level)
            log_level = cls._python_to_pyo3_level(log_level)
            if cls.log_function:
                cls.log_function(log_level, logger_level, log_identifier, message)

        else:
            c_identifier = cls._ffi.new("char[]", log_identifier.encode(ENCODING))
            c_message = cls._ffi.new("char[]", message.encode(ENCODING))

            cls._lib.log(
                log_level.value, Logger.logger_level.value, c_identifier, c_message
            )

    @classmethod
    def _init_ffi(cls):
        cls._ffi = FFI()
        cls._ffi.cdef(
            """
            typedef enum {
                Error = 0,
                Warn = 1,
                Info = 2,
                Debug = 3,
                Trace = 4,
                Off = 5
            } Level;

            Level init(const Level* level, const char* file_name);
            void log(Level level, Level logger_level, const char* identifier, const char* message);
        """
        )
        cls._lib = cls._ffi.dlopen(str(LIB_FILE.resolve()))
        cls.log_function = cls._lib.log

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
        Logger._instance = Logger(level, file_name)

    @staticmethod
    def _python_to_pyo3_level(level: Optional[Level]):
        from .glide import Level as Pyo3Level

        if level is None:
            return None
        elif level.value == 0:
            return Pyo3Level.Error
        elif level.value == 1:
            return Pyo3Level.Warn
        elif level.value == 2:
            return Pyo3Level.Info
        elif level.value == 3:
            return Pyo3Level.Debug
        elif level.value == 4:
            return Pyo3Level.Trace
        elif level.value == 5:
            return Pyo3Level.Off
        else:
            raise TypeError(f"Invalid ltevel: {level}")
