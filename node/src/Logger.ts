import { InitInternalLogger, Level, log } from "babushka-rs-internal";

const LEVEL: Map<LevelOptions | undefined, Level | undefined> = new Map([
    ["error", Level.Error],
    ["warn", Level.Warn],
    ["info", Level.Info],
    ["debug", Level.Debug],
    ["trace", Level.Trace],
    [undefined, undefined],
]);
type LevelOptions = "error" | "warn" | "info" | "debug" | "trace";

/*
A class that allows logging which is consistent with logs from the internal rust core.
Only one instance of this class can exist at any given time. The logger can be set up in 2 ways -
    1. By calling init, which creates and modifies a new logger only if one doesn't exist.
    2. By calling setConfig, which replaces the existing logger, and means that new logs will not be saved with the logs that were sent before the call.
If no call to any of these function is received, the first log attempt will initialize a new logger with default level decided by rust core (normally - console, error).
External users shouldn't user Logger, and instead setLoggerConfig Before starting to use the client.
*/
export class Logger {
    private static _instance: Logger;
    private static logger_level = 0;

    private constructor(level?: LevelOptions, fileName?: string) {
        Logger.logger_level = InitInternalLogger(LEVEL.get(level), fileName);
    }

    // Initialize a logger instance if none were initialized before - this method is meant to be used when there is no intention to replace an existing logger.
    // The logger will filter all logs with a level lower than the given level,
    // If given a fileName argument, will write the logs to files postfixed with fileName. If fileName isn't provided, the logs will be written to the console.
    public static init(level?: LevelOptions, fileName?: string) {
        return this._instance || (this._instance = new this(level, fileName));
    }

    // returning the logger instance - if doesn't exist initiate new with default config decided by rust core
    public static get instance() {
        return this._instance || (this._instance = new this());
    }

    // config the logger instance - in fact - create new logger instance with the new args
    // exist in addition to init for two main reason's:
    // 1. if Babushka dev want intentionally to change the logger instance configuration
    // 2. external user want to set the logger and we don't want to return to him the logger itself, just config it
    // the level argument is the level of the logs you want the system to provide (error logs, warn logs, etc.)
    // the filename argument is optional - if provided the target of the logs will be the file mentioned, else will be the console
    public static setConfig(level: LevelOptions, fileName?: string) {
        this._instance = new this(level, fileName);
        return;
    }

    // take the arguments from the user and provide to the core-logger (see ../logger-core)
    // if the level is higher then the logger level (error is 0, warn 1, etc.) simply return without operation
    // if a logger instance doesn't exist, create new one with default mode (decided by rust core, normally - level: error, target: console)
    // logIdentifier arg is a string contain data that suppose to give the log a context and make it easier to find certain type of logs.
    // when the log is connect to certain task the identifier should be the task id, when the log is not part of specific task the identifier should give a context to the log - for example, "socket connection".
    public log(logLevel: LevelOptions, logIdentifier: string, message: string) {
        if (!Logger._instance) {
            new Logger();
        }
        const level = LEVEL.get(logLevel) || 0;
        if (!(level <= Logger.logger_level)) return;
        log(level, logIdentifier, message);
    }
}

// This function is the interface for logging that will be provided to external user
// for more details see setConfig function above
export function setLoggerConfig(level: LevelOptions, fileName?: string) {
    Logger.setConfig(level, fileName);
}
