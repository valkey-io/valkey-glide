// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

package glide

// #include "lib.h"
// void monitorCallback(uintptr_t clientPtr, double timestamp, int64_t db,
//                      void *clientAddr, int64_t clientAddrLen,
//                      void *command, int64_t commandLen,
//                      void *argsJson, int64_t argsJsonLen);
import "C"

import (
	"encoding/json"
	"sync"
	"unsafe"

	"github.com/valkey-io/valkey-glide/go/v2/config"
	"google.golang.org/protobuf/proto"
)

// MonitorLine represents a single line received from the MONITOR command.
type MonitorLine struct {
	Timestamp  float64
	DB         int64
	ClientAddr string
	Command    string
	Args       []string
}

var (
	monitorRegistry   = make(map[uintptr]*MonitorClient)
	monitorRegistryMu sync.RWMutex
)

func registerMonitorClient(client *MonitorClient, ptrValue uintptr) {
	monitorRegistryMu.Lock()
	defer monitorRegistryMu.Unlock()
	monitorRegistry[ptrValue] = client
}

func unregisterMonitorClient(ptrValue uintptr) {
	monitorRegistryMu.Lock()
	defer monitorRegistryMu.Unlock()
	delete(monitorRegistry, ptrValue)
}

func getMonitorClientByPtr(ptrValue uintptr) *MonitorClient {
	monitorRegistryMu.RLock()
	defer monitorRegistryMu.RUnlock()
	return monitorRegistry[ptrValue]
}

//export monitorCallback
func monitorCallback(
	clientPtr C.uintptr_t,
	timestamp C.double,
	db C.int64_t,
	clientAddr unsafe.Pointer, clientAddrLen C.int64_t,
	command unsafe.Pointer, commandLen C.int64_t,
	argsJson unsafe.Pointer, argsJsonLen C.int64_t,
) {
	client := getMonitorClientByPtr(uintptr(clientPtr))
	if client == nil {
		return
	}

	args := []string{}
	if argsJsonLen > 0 && argsJson != nil {
		jsonBytes := C.GoBytes(argsJson, C.int(argsJsonLen))
		_ = json.Unmarshal(jsonBytes, &args)
	}

	line := MonitorLine{
		Timestamp:  float64(timestamp),
		DB:         int64(db),
		ClientAddr: string(C.GoBytes(clientAddr, C.int(clientAddrLen))),
		Command:    string(C.GoBytes(command, C.int(commandLen))),
		Args:       args,
	}

	client.mu.Lock()
	cb := client.callback
	if cb == nil {
		client.queue = append(client.queue, line)
		client.cond.Broadcast()
		client.mu.Unlock()
		return
	}
	client.activeCBs.Add(1)
	client.mu.Unlock()
	cb(line)
	client.activeCBs.Done()
}

// MonitorClient listens to all commands processed by the server via the MONITOR command.
// It is standalone-only; cluster mode is not supported.
type MonitorClient struct {
	coreClient unsafe.Pointer
	mu         sync.Mutex
	cond       *sync.Cond
	callback   func(MonitorLine)
	queue      []MonitorLine
	closed     bool
	activeCBs  sync.WaitGroup
}

// NewMonitorClient creates a new MonitorClient connected to a standalone server.
// If callback is non-nil, it is called for each received MonitorLine; otherwise messages
// are queued and can be retrieved with GetMonitorMessage or TryGetMonitorMessage.
func NewMonitorClient(cfg *config.ClientConfiguration, callback func(MonitorLine)) (*MonitorClient, error) {
	request, err := cfg.ToProtobuf()
	if err != nil {
		return nil, err
	}
	msg, err := proto.Marshal(request)
	if err != nil {
		return nil, err
	}

	byteCount := len(msg)
	requestBytes := C.CBytes(msg)
	defer C.free(requestBytes)

	cResponse := (*C.struct_ConnectionResponse)(
		C.create_monitor_client(
			(*C.uint8_t)(requestBytes),
			C.uintptr_t(byteCount),
			C.MonitorCallback(unsafe.Pointer(C.monitorCallback)),
		),
	)
	defer C.free_connection_response(cResponse)

	if cErr := cResponse.connection_error_message; cErr != nil {
		return nil, NewConnectionError(C.GoString(cErr))
	}

	client := &MonitorClient{
		coreClient: cResponse.conn_ptr,
		callback:   callback,
	}
	client.cond = sync.NewCond(&client.mu)
	registerMonitorClient(client, uintptr(cResponse.conn_ptr))
	return client, nil
}

// GetMonitorMessage blocks until a MonitorLine is available and returns it.
// Returns an error if the client has been closed.
func (c *MonitorClient) GetMonitorMessage() (MonitorLine, error) {
	c.mu.Lock()
	defer c.mu.Unlock()
	for len(c.queue) == 0 && !c.closed {
		c.cond.Wait()
	}
	if c.closed && len(c.queue) == 0 {
		return MonitorLine{}, NewConnectionError("monitor client is closed")
	}
	line := c.queue[0]
	c.queue = c.queue[1:]
	if len(c.queue) == 0 {
		c.queue = nil
	}
	return line, nil
}

// TryGetMonitorMessage returns a MonitorLine if one is available, or (MonitorLine{}, false) if none.
func (c *MonitorClient) TryGetMonitorMessage() (MonitorLine, bool) {
	c.mu.Lock()
	defer c.mu.Unlock()
	if len(c.queue) == 0 {
		return MonitorLine{}, false
	}
	line := c.queue[0]
	c.queue = c.queue[1:]
	if len(c.queue) == 0 {
		c.queue = nil
	}
	return line, true
}

// Close stops the monitor client and releases its resources.
// Calling Close from within a MonitorClient callback will deadlock.
func (c *MonitorClient) Close() {
	c.mu.Lock()
	if c.closed {
		c.mu.Unlock()
		return
	}
	c.closed = true
	c.mu.Unlock()

	c.cond.Broadcast()
	c.activeCBs.Wait()

	if c.coreClient != nil {
		unregisterMonitorClient(uintptr(c.coreClient))
		C.close_monitor_client(c.coreClient)
		c.coreClient = nil
	}
}
