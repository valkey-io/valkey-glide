/** Copyright GLIDE-for-Redis Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.api.models.commands;

import java.util.ArrayList;
import java.util.List;
import lombok.Builder;

@Builder
public class ScanOptions {
    /** <code>MATCH</code> option string to include in the <code>SCAN</code> commands. */
    public static final String MATCH_OPTION_STRING = "MATCH";

    /** <code>COUNT</code> option string to include in the <code>SCAN</code> commands. */
    public static final String COUNT_OPTION_STRING = "COUNT";

    private final String matchPattern;
    private final Long count;

    public String[] toArgs() {
        List<String> optionArgs = new ArrayList<>();

        if (matchPattern != null) {
            optionArgs.add(MATCH_OPTION_STRING);
            optionArgs.add(matchPattern);
        }

        if (count != null) {
            optionArgs.add(COUNT_OPTION_STRING);
            optionArgs.add(count.toString());
        }

        return optionArgs.toArray(new String[0]);
    }
}
