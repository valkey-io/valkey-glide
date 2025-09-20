/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.api.models.commands.scan;

import glide.api.commands.GenericClusterCommands;

/**
 * A cursor is used to iterate through data returned by cluster SCAN requests.
 *
 * <p>This interface is used in two ways:
 *
 * <ol>
 *   <li>An {@link #initialCursor()} is passed to {@link GenericClusterCommands#scan} to start a
 *       cluster SCAN request.
 *   <li>The result of the {@link GenericClusterCommands#scan} call returns a cursor at index <code>
 *       0</code> of the returned <code>Object[]</code>. This cursor can be supplied again to a call
 *       to {@link GenericClusterCommands#scan}, provided that {@link #isFinished()} returns <code>
 *       false</code>.
 * </ol>
 *
 * <p>Note that cursors returned by {@link GenericClusterCommands#scan} may hold external resources.
 * These resources can be released by calling {@link #releaseCursorHandle()}. However, doing so will
 * invalidate the cursor from being used in another {@link GenericClusterCommands#scan}.
 *
 * <p>To do this safely, follow this procedure:
 *
 * <ol>
 *   <li>Call {@link GenericClusterCommands#scan} with the cursor.
 *   <li>Call {@link #releaseCursorHandle()} to destroy the cursor.
 *   <li>Assign the new cursor returned by {@link GenericClusterCommands#scan}.
 * </ol>
 *
 * @see GenericClusterCommands#scan
 * @example
 *     <pre>{@code
 * ClusterScanCursor cursor = ClusterScanCursor.initialCursor();
 * Object[] result;
 * while (!cursor.isFinished()) {
 *     result = client.scan(cursor).get();
 *     cursor.releaseCursorHandle();
 *     cursor = (ClusterScanCursor) result[0];
 *     Object[] stringResults = (Object[]) result[1];
 *
 *     System.out.println("\nSCAN iteration:");
 *     Arrays.asList(stringResults).stream().forEach(i -> System.out.print(i + ", "));
 * }
 * }</pre>
 */
public interface ClusterScanCursor {
    /**
     * Indicates if this cursor is the last set of data available.
     *
     * <p>If this method returns false, this cursor instance should get passed to {@link
     * GenericClusterCommands#scan} to get next set of data.
     *
     * @return true if this cursor is the last set of data. False if there is potentially more data
     *     available.
     */
    boolean isFinished();

    /**
     * Releases resources related to this cursor.
     *
     * <p>This method can be called to immediately release resources tied to this cursor instance.
     * Note that if this is called, this cursor cannot be used in {@link GenericClusterCommands#scan}.
     * Also, this method is optional for the caller. If it does not get called, cursor resources will
     * be freed during garbage collection.
     */
    void releaseCursorHandle();

    /**
     * The special cursor used to start the first in a series of {@link GenericClusterCommands#scan}
     * calls.
     */
    ClusterScanCursor INITIAL_CURSOR_INSTANCE =
            new ClusterScanCursor() {
                @Override
                public boolean isFinished() {
                    // The initial cursor can always request more data.
                    return false;
                }

                @Override
                public void releaseCursorHandle() {
                    // Ignore.
                }
            };

    /** Creates an empty cursor to be used in the initial {@link GenericClusterCommands#scan} call. */
    static ClusterScanCursor initialCursor() {
        return INITIAL_CURSOR_INSTANCE;
    }

    /**
     * Creates an empty cursor to be used in the initial {@link GenericClusterCommands#scan} call.
     *
     * @deprecated Use {@link #initialCursor()} instead.
     */
    @Deprecated
    static ClusterScanCursor initalCursor() {
        return initialCursor();
    }
}
