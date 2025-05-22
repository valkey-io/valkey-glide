// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

package options

import (
	"sync"
	"unsafe"
)

// #include "../lib.h"
import "C"

// ScriptOptions represents options for script execution
type ScriptOptions struct {
	keys []string
	args []string
}

// NewScriptOptions creates a new ScriptOptions with default values
func NewScriptOptions() *ScriptOptions {
	return &ScriptOptions{
		keys: []string{},
		args: []string{},
	}
}

// WithKeys sets the keys for the script
func (o *ScriptOptions) WithKeys(keys []string) *ScriptOptions {
	o.keys = keys
	return o
}

// WithArgs sets the arguments for the script
func (o *ScriptOptions) WithArgs(args []string) *ScriptOptions {
	o.args = args
	return o
}

// GetKeys returns the keys for the script
func (o *ScriptOptions) GetKeys() []string {
	return o.keys
}

// GetArgs returns the arguments for the script
func (o *ScriptOptions) GetArgs() []string {
	return o.args
}

// ScriptArgOptions represents options for script execution with only arguments
type ScriptArgOptions struct {
	args []string
}

// NewScriptArgOptions creates a new ScriptArgOptions with default values
func NewScriptArgOptions() *ScriptArgOptions {
	return &ScriptArgOptions{
		args: []string{},
	}
}

// WithArgs sets the arguments for the script
func (o *ScriptArgOptions) WithArgs(args []string) *ScriptArgOptions {
	o.args = args
	return o
}

// GetArgs returns the arguments for the script
func (o *ScriptArgOptions) GetArgs() []string {
	return o.args
}

type ClusterScriptOptions struct {
	*ScriptArgOptions
	*RouteOption
}

// NewClusterScriptOptions creates a new ClusterScriptOptions with default values
func NewClusterScriptOptions() *ClusterScriptOptions {
	return &ClusterScriptOptions{
		ScriptArgOptions: NewScriptArgOptions(),
		RouteOption:      &RouteOption{},
	}
}

// WithRouteOptions sets the route options for the cluster script
func (o *ClusterScriptOptions) WithRouteOptions(routeOption *RouteOption) *ClusterScriptOptions {
	o.RouteOption = routeOption
	return o
}

// WithScriptArgOptions sets the script arg options for the cluster script
func (o *ClusterScriptOptions) WithScriptArgOptions(scriptArgOptions *ScriptArgOptions) *ClusterScriptOptions {
	o.ScriptArgOptions = scriptArgOptions
	return o
}

// Script represents a Lua script stored in Valkey/Redis
type Script struct {
	hash      string
	isDropped bool
	mu        *sync.Mutex
}

// NewScript creates a new Script object
func NewScript(code string) *Script {
	// In Go implementation, we'd convert code to bytes and store the script
	hash := storeScript(getBytes(code))
	return &Script{
		hash:      hash,
		isDropped: false,
		mu:        new(sync.Mutex),
	}
}

// GetHash returns the hash of the script
func (s *Script) GetHash() string {
	return s.hash
}

// Close drops the script from memory
func (s *Script) Close() error {
	s.mu.Lock()
	defer s.mu.Unlock()

	if !s.isDropped {
		dropScript(s.hash)
		s.isDropped = true
	}
	return nil
}

// getBytes returns the bytes representation of the string
func getBytes(s string) []byte {
	return []byte(s)
}

// storeScript stores a Lua script in the script cache and returns its SHA1 hash
// This function interfaces with the Rust implementation through FFI
func storeScript(script []byte) string {
	if len(script) == 0 {
		return ""
	}

	cHash := C.store_script(
		(*C.uint8_t)(unsafe.Pointer(&script[0])),
		C.uintptr_t(len(script)),
	)
	defer C.free(unsafe.Pointer(cHash))

	return C.GoString(cHash)
}

// dropScript removes a script from the script cache
// This function interfaces with the Rust implementation through FFI
func dropScript(hash string) {
	if hash == "" {
		return
	}

	cHash := C.CString(hash)
	defer C.free(unsafe.Pointer(cHash))

	C.drop_script(cHash)
}

// ScriptFlushOptions represents options for script flush operations
type ScriptFlushOptions struct {
	Mode  FlushMode
	Route *RouteOption
}

// NewScriptFlushOptions creates a new ScriptFlushOptions with default values
func NewScriptFlushOptions() *ScriptFlushOptions {
	return &ScriptFlushOptions{
		Mode:  "",
		Route: &RouteOption{},
	}
}

// WithMode sets the flush mode for the script flush operation
func (o *ScriptFlushOptions) WithMode(mode FlushMode) *ScriptFlushOptions {
	o.Mode = mode
	return o
}

// WithRoute sets the route option for the script flush operation
func (o *ScriptFlushOptions) WithRoute(route *RouteOption) *ScriptFlushOptions {
	o.Route = route
	return o
}
