// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

package interfaces

type BaseClientCommands interface {
	StringCommands
	HashCommands
	ListCommands
	SetCommands
	StreamCommands
	SortedSetCommands
	HyperLogLogCommands
	GenericBaseCommands
	BitmapCommands
	GeoSpatialCommands
	ScriptingAndFunctionBaseCommands
	PubSubCommands
	// Close terminates the client by closing all associated resources.
	Close()
}

type GlideClientCommands interface {
	BaseClientCommands
	GenericCommands
	ServerManagementCommands
	BitmapCommands
	ConnectionManagementCommands
	ScriptingAndFunctionStandaloneCommands
	PubSubStandaloneCommands
}

type GlideClusterClientCommands interface {
	BaseClientCommands
	GenericClusterCommands
	ServerManagementClusterCommands
	ConnectionManagementClusterCommands
	ScriptingAndFunctionClusterCommands
	PubSubClusterCommands
}
