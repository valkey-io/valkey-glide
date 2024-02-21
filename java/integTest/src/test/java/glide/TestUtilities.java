/** Copyright GLIDE-for-Redis Project Contributors - SPDX Identifier: Apache-2.0 */
package glide;

import glide.api.models.ClusterValue;
import lombok.experimental.UtilityClass;

@UtilityClass
public class TestUtilities {

    /** Extract first value from {@link ClusterValue} assuming it contains a multi-value. */
    public static <T> T getFirstEntryFromMultiValue(ClusterValue<T> data) {
        return data.getMultiValue().get(data.getMultiValue().keySet().toArray(String[]::new)[0]);
    }
}
