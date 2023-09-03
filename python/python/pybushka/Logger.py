from enum import Enum
from typing import Optional

from .pybushka import Level as internalLevel
from .pybushka import py_init, py_log


class Level(Enum):
    ERROR = internalLevel.Error
    WARN = internalLevel.Warn
    INFO = internalLevel.Info
    DEBUG = internalLevel.Debug
    TRACE = internalLevel.Trace


class Logger:
    """
    A class that allows logging which is consistent with logs from the internal rust core.
    Logger.set_logger_config should be called before starting to use the client.
    The loggeing setting can be changed by calling set_logger_config, which replaces the existing logger, and means that new logs will
    not be saved with the logs that were sent before the call.
    If set_logger_config wasn't called, the first log attempt will initialize a new logger with default level (console, WARN).
    """

    _instance = None
    logger_level = None

    def __init__(self, level: Optional[Level] = None, file_name: str = None) -> super:
        if level is not None:
            level = level.value
        Logger.logger_level = py_init(level, file_name)

    @classmethod
    def log(cls, log_level: Level, log_identifier: str, message: str):
        """Logs the provided message if the provided log level is lower then the logger level.

        Args:
            log_level (Level): The log level of the provided message
            log_identifier (str): The log identifier should give the log a context.
            message (str): The message to log.
        """
        if not cls._instance:
            cls._instance = cls(None)
        if not log_level.value.is_lower(Logger.logger_level):
            return
        py_log(log_level.value, log_identifier, message)

    @classmethod
    def set_logger_config(
        cls, level: Optional[Level] = None, file_name: Optional[str] = None
    ):
        """Creates a new logger instance and configure it with the provided log level and file name.

        Args:
            level (Optional[Level]): Set the logger level to one of [ERROR, WARN, INFO, DEBUG, TRACE].
                If log level isn't provided, the default level will be set to WARN.
            file_name (Optional[str]):  If providedv the target of the logs will be the file mentioned.
                Otherwise, logs will be printed to the console.
        """
        Logger._instance = Logger(level, file_name)
