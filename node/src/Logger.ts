/**
 * Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0
 */

import { InitInternalLogger, Level, log } from ".";

const LEVEL = new Map<LevelOptions | undefined, Level | undefined>([
    ["error", Level.Error],
    ["warn", Level.Warn],
    ["info", Level.Info],
    ["debug", Level.Debug],
    ["trace", Level.Trace],
    ["off", Level.Off],
    [undefined, undefined],
]);
export type LevelOptions =
    | "error"
    | "warn"
    | "info"
    | "debug"
    | "trace"
    | "off";

/**
 * A singleton class that allows logging which is consistent with logs from the internal GLIDE core.
 * The logger can be set up in 2 ways -
 *   1. By calling {@link init}, which configures the logger only if it wasn't previously configured.
 *   2. By calling {@link setLoggerConfig}, which replaces the existing configuration, and means that new logs
 *     will not be saved with the logs that were sent before the call. The previous logs will remain unchanged.
 * If none of these functions are called, the first log attempt will initialize a new logger with default configuration.
 */
export class Logger {
    private static _instance: Logger;
    private static logger_level = 0;

    private constructor(level?: LevelOptions, fileName?: string) {
        Logger.logger_level = InitInternalLogger(LEVEL.get(level), fileName);
    }

    /**
     * Logs the provided message if the provided log level is lower then the logger level.
     *
     * @param logLevel - The log level of the provided message.
     * @param logIdentifier - The log identifier should give the log a context.
     * @param message - The message to log.
     * @param err - The exception or error to log.
     */
    public static log(
        logLevel: LevelOptions,
        logIdentifier: string,
        message: string,
        err?: Error,
    ) {
        if (!Logger._instance) {
            new Logger();
        }

        if (err) {
            message += `: ${err.stack}`;
        }

        const level = LEVEL.get(logLevel) || 0;
        if (!(level <= Logger.logger_level)) return;
        log(level, logIdentifier, message);
    }

    /**
     * Initialize a logger if it wasn't initialized before - this method is meant to be used when there is no intention to
     * replace an existing logger.
     * The logger will filter all logs with a level lower than the given level.
     * If given a fileName argument, will write the logs to files postfixed with fileName. If fileName isn't provided,
     * the logs will be written to the console.
     *
     * @param level - Set the logger level to one of [ERROR, WARN, INFO, DEBUG, TRACE, OFF].
     *   If log level isn't provided, the logger will be configured with default configuration.
     *   To turn off logging completely, set the level to level "off".
     * @param fileName - If provided the target of the logs will be the file mentioned.
     *   Otherwise, logs will be printed to the console.
     */
    public static init(level?: LevelOptions, fileName?: string) {
        if (!this._instance) {
            this._instance = new this(level, fileName);
        }
    }

    /**
     * Creates a new logger instance and configure it with the provided log level and file name.
     *
     * @param level - Set the logger level to one of [ERROR, WARN, INFO, DEBUG, TRACE, OFF].
     * @param fileName - The target of the logs will be the file mentioned.
     */
    public static setLoggerConfig(level: LevelOptions, fileName?: string) {
        this._instance = new this(level, fileName);
    }
}
