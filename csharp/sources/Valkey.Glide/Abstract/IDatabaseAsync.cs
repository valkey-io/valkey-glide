// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

using Valkey.Glide.Commands;

namespace Valkey.Glide;

public interface IDatabaseAsync : IConnectionManagementCommands, IGenericCommands, IGenericBaseCommands, IHashCommands, IListCommands, IServerManagementCommands, ISetCommands, ISortedSetCommands, IStringBaseCommands
{ }
