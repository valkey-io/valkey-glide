/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.api.commands;

import glide.api.models.commands.FlushMode;
import glide.api.models.commands.InfoOptions.Section;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Supports commands for the "Server Management" group for a standalone client.
 *
 * @see <a href="https://valkey.io/commands/?group=server">Server Management Commands</a>
 */
public interface ServerManagementCommands {

    /** A keyword for {@link #lolwut(int)} and {@link #lolwut(int, int[])}. */
    String VERSION_VALKEY_API = "VERSION";

    /**
     * Gets information and statistics about the server using the {@link Section#DEFAULT} option.
     *
     * @see <a href="https://valkey.io/commands/info/">valkey.io</a> for details.
     * @return A <code>String</code> with the information for the default sections.
     * @example
     *     <pre>{@code
     * String response = client.info().get();
     * assert response.contains("# Stats");
     * }</pre>
     */
    CompletableFuture<String> info();

    /**
     * Get information and statistics about the server.<br>
     * Starting from server version 7, command supports multiple section arguments.
     *
     * @see <a href="https://valkey.io/commands/info/">valkey.io</a> for details.
     * @param sections A list of {@link Section} values specifying which sections of information to
     *     retrieve. When no parameter is provided, the {@link Section#DEFAULT} option is assumed.
     * @return A <code>String</code> containing the information for the sections requested.
     * @example
     *     <pre>{@code
     * String response = regularClient.info(new Section[] { Section.STATS }).get();
     * assert response.contains("total_net_input_bytes");
     * }</pre>
     */
    CompletableFuture<String> info(Section[] sections);

    /**
     * Rewrites the configuration file with the current configuration.
     *
     * @see <a href="https://valkey.io/commands/config-rewrite/">valkey.io</a> for details.
     * @return <code>OK</code> when the configuration was rewritten properly, otherwise an error is
     *     thrown.
     * @example
     *     <pre>{@code
     * String response = client.configRewrite().get();
     * assert response.equals("OK");
     * }</pre>
     */
    CompletableFuture<String> configRewrite();

    /**
     * Resets the statistics reported by the server using the <a
     * href="https://valkey.io/commands/info/">INFO</a> and <a
     * href="https://valkey.io/commands/latency-histogram/">LATENCY HISTOGRAM</a> commands.
     *
     * @see <a href="https://valkey.io/commands/config-resetstat/">valkey.io</a> for details.
     * @return <code>OK</code> to confirm that the statistics were successfully reset.
     * @example
     *     <pre>{@code
     * String response = client.configResetStat().get();
     * assert response.equals("OK");
     * }</pre>
     */
    CompletableFuture<String> configResetStat();

    /**
     * Get the values of configuration parameters.<br>
     * Starting from server version 7, command supports multiple parameters.
     *
     * @see <a href="https://valkey.io/commands/config-get/">valkey.io</a> for details.
     * @param parameters An <code>array</code> of configuration parameter names to retrieve values
     *     for.
     * @return A <code>map</code> of values corresponding to the configuration parameters.
     * @example
     *     <pre>{@code
     * Map<String, String> configParams = client.configGet(new String[] {"timeout" , "maxmemory"}).get();
     * assert configParams.get("timeout").equals("1000");
     * assert configParams.get("maxmemory").equals("1GB");
     * }</pre>
     */
    CompletableFuture<Map<String, String>> configGet(String[] parameters);

    /**
     * Sets configuration parameters to the specified values.<br>
     * Starting from server version 7, command supports multiple parameters.
     *
     * @see <a href="https://valkey.io/commands/config-set/">valkey.io</a> for details.
     * @param parameters A <code>map</code> consisting of configuration parameters and their
     *     respective values to set.
     * @return <code>OK</code> if all configurations have been successfully set. Otherwise, raises an
     *     error.
     * @example
     *     <pre>{@code
     * String response = client.configSet(Map.of("timeout", "1000", "maxmemory", "1GB")).get();
     * assert response.equals("OK");
     * }</pre>
     */
    CompletableFuture<String> configSet(Map<String, String> parameters);

    /**
     * Returns the server time.
     *
     * @see <a href="https://valkey.io/commands/time/">valkey.io</a> for details.
     * @return The current server time as a <code>String</code> array with two elements: A <code>
     *     UNIX TIME</code> and the amount of microseconds already elapsed in the current second. The
     *     returned array is in a <code>[UNIX TIME, Microseconds already elapsed]</code> format.
     * @example
     *     <pre>{@code
     * String[] serverTime = client.time().get();
     * System.out.println("Server time is: " + serverTime[0] + "." + serverTime[1]);
     * }</pre>
     */
    CompletableFuture<String[]> time();

    /**
     * Returns <code>UNIX TIME</code> of the last DB save timestamp or startup timestamp if no save
     * was made since then.
     *
     * @see <a href="https://valkey.io/commands/lastsave/">valkey.io</a> for details.
     * @return <code>UNIX TIME</code> of the last DB save executed with success.
     * @example
     *     <pre>{@code
     * Long timestamp = client.lastsave().get();
     * System.out.printf("Last DB save was done at %s%n", Instant.ofEpochSecond(timestamp));
     * }</pre>
     */
    CompletableFuture<Long> lastsave();

    /**
     * Deletes all the keys of all the existing databases. This command never fails.
     *
     * @see <a href="https://valkey.io/commands/flushall/">valkey.io</a> for details.
     * @return <code>OK</code>.
     * @example
     *     <pre>{@code
     * String response = client.flushall().get();
     * assert response.equals("OK");
     * }</pre>
     */
    CompletableFuture<String> flushall();

    /**
     * Deletes all the keys of all the existing databases. This command never fails.
     *
     * @see <a href="https://valkey.io/commands/flushall/">valkey.io</a> for details.
     * @param mode The flushing mode, could be either {@link FlushMode#SYNC} or {@link
     *     FlushMode#ASYNC}.
     * @return <code>OK</code>.
     * @example
     *     <pre>{@code
     * String response = client.flushall(ASYNC).get();
     * assert response.equals("OK");
     * }</pre>
     */
    CompletableFuture<String> flushall(FlushMode mode);

    /**
     * Deletes all the keys of the currently selected database. This command never fails.
     *
     * @see <a href="https://valkey.io/commands/flushdb/">valkey.io</a> for details.
     * @return <code>OK</code>.
     * @example
     *     <pre>{@code
     * String response = client.flushdb().get();
     * assert response.equals("OK");
     * }</pre>
     */
    CompletableFuture<String> flushdb();

    /**
     * Deletes all the keys of the currently selected database. This command never fails.
     *
     * @see <a href="https://valkey.io/commands/flushdb/">valkey.io</a> for details.
     * @param mode The flushing mode, could be either {@link FlushMode#SYNC} or {@link
     *     FlushMode#ASYNC}.
     * @return <code>OK</code>.
     * @example
     *     <pre>{@code
     * String response = client.flushdb(ASYNC).get();
     * assert response.equals("OK");
     * }</pre>
     */
    CompletableFuture<String> flushdb(FlushMode mode);

    /**
     * Displays a piece of generative computer art and the Valkey version.
     *
     * @see <a href="https://valkey.io/commands/lolwut/">valkey.io</a> for details.
     * @return A piece of generative computer art along with the current Valkey version.
     * @example
     *     <pre>{@code
     * String data = client.lolwut().get();
     * System.out.println(data);
     * assert data.contains("Redis ver. 7.2.3");
     * }</pre>
     */
    CompletableFuture<String> lolwut();

    /**
     * Displays a piece of generative computer art and the Valkey version.
     *
     * @see <a href="https://valkey.io/commands/lolwut/">valkey.io</a> for details.
     * @param parameters Additional set of arguments in order to change the output:
     *     <ul>
     *       <li>On Valkey version <code>5</code>, those are length of the line, number of squares per
     *           row, and number of squares per column.
     *       <li>On Valkey version <code>6</code>, those are number of columns and number of lines.
     *       <li>On other versions parameters are ignored.
     *     </ul>
     *
     * @return A piece of generative computer art along with the current Valkey version.
     * @example
     *     <pre>{@code
     * String data = client.lolwut(new int[] { 40, 20 }).get();
     * System.out.println(data);
     * assert data.contains("Redis ver. 7.2.3");
     * }</pre>
     */
    CompletableFuture<String> lolwut(int[] parameters);

    /**
     * Displays a piece of generative computer art and the Valkey version.
     *
     * @apiNote Versions 5 and 6 produce graphical things.
     * @see <a href="https://valkey.io/commands/lolwut/">valkey.io</a> for details.
     * @param version Version of computer art to generate.
     * @return A piece of generative computer art along with the current Valkey version.
     * @example
     *     <pre>{@code
     * String data = client.lolwut(6).get();
     * System.out.println(data);
     * assert data.contains("Redis ver. 7.2.3");
     * }</pre>
     */
    CompletableFuture<String> lolwut(int version);

    /**
     * Displays a piece of generative computer art and the Valkey version.
     *
     * @apiNote Versions 5 and 6 produce graphical things.
     * @see <a href="https://valkey.io/commands/lolwut/">valkey.io</a> for details.
     * @param version Version of computer art to generate.
     * @param parameters Additional set of arguments in order to change the output:
     *     <ul>
     *       <li>For version <code>5</code>, those are length of the line, number of squares per row,
     *           and number of squares per column.
     *       <li>For version <code>6</code>, those are number of columns and number of lines.
     *     </ul>
     *
     * @return A piece of generative computer art along with the current Valkey version.
     * @example
     *     <pre>{@code
     * String data = client.lolwut(6, new int[] { 40, 20 }).get();
     * System.out.println(data);
     * assert data.contains("Redis ver. 7.2.3");
     * data = client.lolwut(5, new int[] { 30, 5, 5 }).get();
     * System.out.println(data);
     * assert data.contains("Redis ver. 7.2.3");
     * }</pre>
     */
    CompletableFuture<String> lolwut(int version, int[] parameters);

    /**
     * Returns the number of keys in the currently selected database.
     *
     * @see <a href="https://valkey.io/commands/dbsize/">valkey.io</a> for details.
     * @return The number of keys in the currently selected database.
     * @example
     *     <pre>{@code
     * Long numKeys = client.dbsize().get();
     * System.out.printf("Number of keys in the current database: %d%n", numKeys);
     * }</pre>
     */
    CompletableFuture<Long> dbsize();

    /**
     * Returns a list of all ACL categories, or a list of commands within a category.
     *
     * @see <a href="https://valkey.io/commands/acl-cat/">valkey.io</a> for details.
     * @return An array of ACL categories or commands.
     * @example
     *     <pre>{@code
     * String[] categories = client.aclCat().get();
     * assert Arrays.asList(categories).contains("string");
     * }</pre>
     */
    CompletableFuture<String[]> aclCat();

    /**
     * Returns a list of commands within the specified ACL category.
     *
     * @see <a href="https://valkey.io/commands/acl-cat/">valkey.io</a> for details.
     * @param category The ACL category to list commands for.
     * @return An array of commands within the specified category.
     * @example
     *     <pre>{@code
     * String[] commands = client.aclCat("string").get();
     * assert Arrays.asList(commands).contains("get");
     * }</pre>
     */
    CompletableFuture<String[]> aclCat(String category);

    /**
     * Deletes all specified ACL users and terminates their connections.
     *
     * @see <a href="https://valkey.io/commands/acl-deluser/">valkey.io</a> for details.
     * @param usernames An array of usernames to delete.
     * @return The number of users deleted.
     * @example
     *     <pre>{@code
     * Long deletedCount = client.aclDelUser(new String[] {"user1", "user2"}).get();
     * assert deletedCount == 2L;
     * }</pre>
     */
    CompletableFuture<Long> aclDelUser(String[] usernames);

    /**
     * Simulates the execution of a command by a user without actually executing the command.
     *
     * @see <a href="https://valkey.io/commands/acl-dryrun/">valkey.io</a> for details.
     * @param username The username to simulate command execution for.
     * @param command The command to simulate.
     * @param args The command arguments.
     * @return <code>"OK"</code> if the user can execute the command, otherwise an error is returned.
     * @example
     *     <pre>{@code
     * String result = client.aclDryRun("user1", "get", new String[] {"key"}).get();
     * assert result.equals("OK");
     * }</pre>
     */
    CompletableFuture<String> aclDryRun(String username, String command, String[] args);

    /**
     * Generates a random password for ACL users.
     *
     * @see <a href="https://valkey.io/commands/acl-genpass/">valkey.io</a> for details.
     * @return A randomly generated password string.
     * @example
     *     <pre>{@code
     * String password = client.aclGenPass().get();
     * assert password.length() == 64; // Default length
     * }</pre>
     */
    CompletableFuture<String> aclGenPass();

    /**
     * Generates a random password with the specified number of bits for ACL users.
     *
     * @see <a href="https://valkey.io/commands/acl-genpass/">valkey.io</a> for details.
     * @param bits The number of bits for the password (must be a multiple of 4, between 1 and 4096).
     * @return A randomly generated password string.
     * @example
     *     <pre>{@code
     * String password = client.aclGenPass(128).get();
     * assert password.length() == 32; // 128 bits = 32 hex characters
     * }</pre>
     */
    CompletableFuture<String> aclGenPass(int bits);

    /**
     * Returns all ACL rules for the specified user.
     *
     * @see <a href="https://valkey.io/commands/acl-getuser/">valkey.io</a> for details.
     * @param username The username to get ACL rules for.
     * @return An array describing the ACL rules for the user, or <code>null</code> if user doesn't
     *     exist.
     * @example
     *     <pre>{@code
     * Object userInfo = client.aclGetUser("default").get();
     * assert userInfo != null;
     * }</pre>
     */
    CompletableFuture<Object> aclGetUser(String username);

    /**
     * Returns a list of all ACL users and their rules in ACL configuration file format.
     *
     * @see <a href="https://valkey.io/commands/acl-list/">valkey.io</a> for details.
     * @return An array of ACL rules for all users.
     * @example
     *     <pre>{@code
     * String[] aclList = client.aclList().get();
     * assert aclList.length > 0;
     * }</pre>
     */
    CompletableFuture<String[]> aclList();

    /**
     * Reloads ACL rules from the configured ACL configuration file.
     *
     * @see <a href="https://valkey.io/commands/acl-load/">valkey.io</a> for details.
     * @return <code>"OK"</code> on success.
     * @example
     *     <pre>{@code
     * String result = client.aclLoad().get();
     * assert result.equals("OK");
     * }</pre>
     */
    CompletableFuture<String> aclLoad();

    /**
     * Returns the ACL security events log.
     *
     * @see <a href="https://valkey.io/commands/acl-log/">valkey.io</a> for details.
     * @return An array of ACL security events.
     * @example
     *     <pre>{@code
     * Object[] log = client.aclLog().get();
     * System.out.printf("ACL log has %d entries%n", log.length);
     * }</pre>
     */
    CompletableFuture<Object[]> aclLog();

    /**
     * Returns the specified number of ACL security events from the log.
     *
     * @see <a href="https://valkey.io/commands/acl-log/">valkey.io</a> for details.
     * @param count The number of entries to return.
     * @return An array of ACL security events.
     * @example
     *     <pre>{@code
     * Object[] log = client.aclLog(10).get();
     * assert log.length <= 10;
     * }</pre>
     */
    CompletableFuture<Object[]> aclLog(int count);

    /**
     * Saves the current ACL rules to the configured ACL configuration file.
     *
     * @see <a href="https://valkey.io/commands/acl-save/">valkey.io</a> for details.
     * @return <code>"OK"</code> on success.
     * @example
     *     <pre>{@code
     * String result = client.aclSave().get();
     * assert result.equals("OK");
     * }</pre>
     */
    CompletableFuture<String> aclSave();

    /**
     * Creates or modifies an ACL user and its rules.
     *
     * @see <a href="https://valkey.io/commands/acl-setuser/">valkey.io</a> for details.
     * @param username The username for the ACL user.
     * @param rules An array of ACL rules to apply to the user.
     * @return <code>"OK"</code> on success.
     * @example
     *     <pre>{@code
     * String result = client.aclSetUser("user1", new String[] {"on", "+get", "~*"}).get();
     * assert result.equals("OK");
     * }</pre>
     */
    CompletableFuture<String> aclSetUser(String username, String[] rules);

    /**
     * Returns a list of all ACL usernames.
     *
     * @see <a href="https://valkey.io/commands/acl-users/">valkey.io</a> for details.
     * @return An array of ACL usernames.
     * @example
     *     <pre>{@code
     * String[] users = client.aclUsers().get();
     * assert Arrays.asList(users).contains("default");
     * }</pre>
     */
    CompletableFuture<String[]> aclUsers();

    /**
     * Returns the username of the current connection.
     *
     * @see <a href="https://valkey.io/commands/acl-whoami/">valkey.io</a> for details.
     * @return The username of the current connection.
     * @example
     *     <pre>{@code
     * String username = client.aclWhoami().get();
     * assert username.equals("default");
     * }</pre>
     */
    CompletableFuture<String> aclWhoami();
}
