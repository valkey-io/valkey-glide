from .pybushka import Level, py_init, py_log

LEVEL = {
    "error": Level.Error,
    "warn": Level.Warn,
    "info": Level.Info,
    "debug": Level.Debug,
    "trace": Level.Trace,
}

# A class that allows logging which is consistent with logs from the internal rust core.
# Only one instance of this class can exist at any given time. The logger can be set up in 2 ways -
#     1. By calling init, which creates and modifies a new logger only if one doesn't exist.
#     2. By calling setConfig, which replaces the existing logger, and means that new logs will not be saved with the logs that were sent before the call.
# If no call to any of these function is received, the first log attempt will initialize a new logger with default level decided by rust core (normally - console, error).
# External users shouldn't user Logger, and instead setLoggerConfig Before starting to use the client.
class Logger(object):
    _instance = None
    logger_level = 0

    def __init__(self, level: str = None, file_name: str = None) -> super:
        Logger.logger_level = py_init(LEVEL.get(level), file_name)

    # Initialize a logger instance if none were initialized before - this method is meant to be used when there is no intention to replace an existing logger.
    # The logger will filter all logs with a level lower than the given level,
    # If given a file_name argument, will write the logs to files postfixed with file_name. If file_name isn't provided, the logs will be written to the console.
    @staticmethod
    def init(level: str = None, file_name: str = None) -> super:
        if Logger._instance:
            return Logger._instance
        Logger._instance = Logger(level, file_name)
        return Logger._instance

    # returning the logger instance - if doesn't exist initiate new with default config decided by rust core
    @staticmethod
    def instance() -> super:
        if Logger._instance:
            return Logger._instance
        Logger._instance = Logger()

    # config the logger instance - in fact - create new logger instance with the new args
    # exist in addition to init for two main reason's:
    # 1. if Babushka dev want intentionally to change the logger instance configuration
    # 2. external user want to set the logger and we don't want to return to him the logger itself, just config it
    # the level argument is the level of the logs you want the system to provide (error logs, warn logs, etc.)
    # the file_name argument is optional - if provided the target of the logs will be the file mentioned, else will be the console
    @staticmethod
    def set_config(level: str = None, file_name: str = None) -> super:
        Logger._instance = Logger(level, file_name)
        return

    # take the arguments from the user and provide to the core-logger (see ../logger-core)
    # if the level is higher then the logger level (error is 0, warn 1, etc.) simply return without operation
    # if a logger instance doesn't exist, create new one with default mode (decided by rust core, normally - level: error, target: console)
    # logIdentifier arg is a string contain data that suppose to give the log a context and make it easier to find certain type of logs.
    # when the log is connect to certain task the identifier should be the task id, when the log is not part of specific task the identifier should give a context to the log - for example, "socket connection".
    def log(log_level: str, log_identifier: str, message: str):
        if not Logger._instance:
            Logger()
        level = LEVEL[log_level]
        if not level.is_lower(Logger.logger_level):
            return
        py_log(level, log_identifier, message)


# This function is the interface for logging that will be provided to external user
# for more details see setConfig function above
def set_logger_config(level: str = None, file_name: str = None):
    Logger.set_config(level, file_name)
