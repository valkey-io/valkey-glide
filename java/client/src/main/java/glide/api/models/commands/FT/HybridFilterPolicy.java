/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.api.models.commands.FT;

/**
 * Controls how a VSIM pre-filter (see {@link FTHybridVsimFilter}) is applied against the vector
 * search. Only meaningful inside a counted VSIM FILTER block -- valkey-search's existing uncounted
 * {@code FILTER <expr>} extension on VSIM (no leading token count) cannot carry a policy at all;
 * the server requires the counted form to accept POLICY. This is a genuinely different grammar axis
 * from {@link FTHybridCombine}'s RRF/LINEAR/FUNCTION method -- one controls how the vector search
 * itself is pre-filtered, the other controls how the SEARCH and VSIM arms' scores are fused after
 * both arms have already run.
 */
public enum HybridFilterPolicy {
    /**
     * Evaluates the filter against every candidate as it is produced (brute-force), rather than
     * pre-restricting the vector index's own search space. {@code EF_RUNTIME} on the same VSIM arm is
     * rejected when this policy is set ("EF_RUNTIME is irrelevant for POLICY ADHOC").
     */
    ADHOC,
    /**
     * Evaluates the filter in batches drawn from the vector index. Required for {@code batchSize} to
     * be set on {@link FTHybridVsimFilter} ("BATCH_SIZE requires POLICY BATCHES").
     */
    BATCHES
}
