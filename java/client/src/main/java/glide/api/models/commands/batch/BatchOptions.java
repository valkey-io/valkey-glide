/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.api.models.commands.batch;

import lombok.Getter;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

/** Options for a batch request with settings inherited from {@link BaseBatchOptions}. */
@Getter
@SuperBuilder
@ToString
public class BatchOptions extends BaseBatchOptions {}
