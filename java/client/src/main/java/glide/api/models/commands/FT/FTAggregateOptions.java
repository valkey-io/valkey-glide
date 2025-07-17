/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.api.models.commands.FT;

/**
 * Stub implementation of FT.AGGREGATE options for compilation compatibility.
 * This is a basic stub to allow tests to compile and will need full implementation.
 */
public class FTAggregateOptions {
    
    // Basic stub - constructor
    public FTAggregateOptions() {
    }
    
    // Stub class for GroupBy operations
    public static class GroupBy {
        public GroupBy(String[] fields) {
        }
        
        // Stub nested class for Reducer
        public static class Reducer {
            public Reducer(String function, String alias) {
            }
        }
    }
    
    // Stub class for Apply operations
    public static class Apply {
        public Apply(String expression, String alias) {
        }
    }
    
    // Stub class for SortBy operations
    public static class SortBy {
        public SortBy(String field, boolean ascending) {
        }
        
        // Stub nested class for SortProperty
        public static class SortProperty {
            public SortProperty(String field, boolean ascending) {
            }
        }
        
        // Stub nested class for SortOrder
        public static class SortOrder {
            public static final SortOrder ASC = new SortOrder();
            public static final SortOrder DESC = new SortOrder();
        }
    }
    
    // Stub method for setting group by
    public FTAggregateOptions groupBy(GroupBy groupBy) {
        return this;
    }
    
    // Stub method for setting apply
    public FTAggregateOptions apply(Apply apply) {
        return this;
    }
    
    // Stub method for setting sort by
    public FTAggregateOptions sortBy(SortBy sortBy) {
        return this;
    }
}