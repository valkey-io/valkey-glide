// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

namespace Valkey.Glide.Pipeline;

// TODO docs for the god of docs
public sealed class Batch : BaseBatch<Batch>
{
    public Batch(bool isAtomic) : base(isAtomic)
    {
        // Standalone commands: select, move, copy, scan
    }
}
