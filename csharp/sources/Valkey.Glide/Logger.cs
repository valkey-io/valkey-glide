// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

using System.Runtime.InteropServices;
using System.Text;

namespace Valkey.Glide;

// TODO - use a bindings generator to create this enum.
public enum Level
{
    Error = 0,
    Warn = 1,
    Info = 2,
    Debug = 3,
    Trace = 4,
    Off = 5,
}

/// <summary>
/// A singleton class that allows logging which is consistent with logs from the internal GLIDE core.
/// The logger can be set up in 2 ways:
/// <list type="number">
///   <item>
///     By calling <see cref="Init(Level, string?)" />, which configures the logger only if it wasn't previously configured.
///   </item>
///   <item>
///      By calling <see cref="SetLoggerConfig(Level, string?)" />, which replaces the existing configuration, and means that
///      new logs will not be saved with the logs that were sent before the call.
///   </item>
/// </list>
/// If no call to any of these function is received, the first log attempt will initialize a new logger with default configuration.
/// </summary>
public class Logger
{
    #region private fields

    private static Level? s_loggerLevel = null;
    #endregion private fields

    #region public methods
    /// <summary>
    /// Initialize a logger if it wasn't initialized before - this method is meant to be used when there is no intention to
    /// replace an existing logger.<br />
    /// The logger will filter all logs with a level lower than the given level,
    /// If given a <paramref name="filename"/> argument, will write the logs to files postfixed with <paramref name="filename"/>.
    /// If <paramref name="filename"/> isn't provided, the logs will be written to the console.
    /// </summary>
    /// <param name="level">
    /// Set the logger level to one of <c>[ERROR, WARN, INFO, DEBUG, TRACE, OFF]</c>.
    /// If log level isn't provided, the logger will be configured with default configuration.
    /// To turn off logging completely, set the level to <see cref="Level.Off"/>.
    /// </param>
    /// <param name="filename">
    /// If provided the target of the logs will be the file mentioned.<br />
    /// Otherwise, logs will be printed to the console.
    /// </param>
    public static void Init(Level level, string? filename = null) => SetLoggerConfig(level, filename);

    /// <summary>
    /// Logs the provided message if the provided log level is lower then the logger level.
    /// </summary>
    /// <param name="logLevel">The log level of the provided message.</param>
    /// <param name="logIdentifier">The log identifier should give the log a context.</param>
    /// <param name="message">The message to log.</param>
    /// <param name="error">The exception or error to log.</param>
    public static void Log(Level logLevel, string logIdentifier, string message, Exception? error = null)
    {
        if (s_loggerLevel is null)
        {
            SetLoggerConfig(logLevel);
        }
        if (!(logLevel <= s_loggerLevel))
        {
            return;
        }
        if (error is not null)
        {
            message += $": {error}";
        }
        log(Convert.ToInt32(logLevel), Encoding.UTF8.GetBytes(logIdentifier), Encoding.UTF8.GetBytes(message));
    }

    /// <summary>
    /// Creates a new logger instance and configure it with the provided log level and file name.
    /// </summary>
    /// <param name="level">
    /// Set the logger level to one of <c>[ERROR, WARN, INFO, DEBUG, TRACE, OFF]</c>.
    /// If log level isn't provided, the logger will be configured with default configuration.
    /// To turn off logging completely, set the level to <see cref="Level.Off"/>.
    /// </param>
    /// <param name="filename">
    /// If provided the target of the logs will be the file mentioned.<br />
    /// Otherwise, logs will be printed to the console.
    /// </param>
    public static void SetLoggerConfig(Level level, string? filename = null)
    {
        byte[]? buffer = filename is null ? null : Encoding.UTF8.GetBytes(filename);
        s_loggerLevel = InitInternalLogger(Convert.ToInt32(level), buffer);
    }
    #endregion public methods

    #region FFI function declaration
    [DllImport("libglide_rs", CallingConvention = CallingConvention.Cdecl, EntryPoint = "log")]
    private static extern void log(int logLevel, byte[] logIdentifier, byte[] message);

    [DllImport("libglide_rs", CallingConvention = CallingConvention.Cdecl, EntryPoint = "init")]
    private static extern Level InitInternalLogger(int level, byte[]? filename);

    #endregion
}
