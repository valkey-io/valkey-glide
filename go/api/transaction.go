// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

package api

// #include "../lib.h"
// void successCallback(void *channelPtr, struct CommandResponse *message);
// void failureCallback(void *channelPtr, char *errMessage, RequestErrorType errType);
import "C"

import (
	"fmt"
	"unsafe"

	"github.com/valkey-io/valkey-glide/go/api/config"
	"github.com/valkey-io/valkey-glide/go/api/errors"
	"google.golang.org/protobuf/proto"
)

type Transaction struct {
	*baseClient // Embed baseClient to inherit all methods like Get
	*GlideClient
	*GlideClusterClient
	commands []Cmder
}

// TransactionOptions contains configurable options for transaction execution
type TransactionOption struct {
	RaiseOnError bool
	Timeout      uint32
}

type Cmder interface {
	Name() C.RequestType
	Args() []string
	Route() config.Route
}

type GenericCommand struct {
	name  C.RequestType
	args  []string
	route config.Route
}

func (cmd *GenericCommand) Name() C.RequestType {
	return cmd.name
}

func (cmd *GenericCommand) Args() []string {
	return cmd.args
}

func (cmd *GenericCommand) Route() config.Route {
	return cmd.route
}

func NewMultiCommand() Cmder {
	return &GenericCommand{name: C.Multi, args: []string{}}
}

func NewExecCommand() Cmder {
	return &GenericCommand{name: C.Exec, args: []string{}}
}

// Override sendCommand to queue commands in the transaction
func (t *Transaction) sendCommand(requestType C.RequestType, args []string) (*C.struct_CommandResponse, error) {
	t.commands = append(t.commands, &GenericCommand{name: requestType, args: args})
	return nil, nil // Queue the command instead of executing immediately
}

// Override sendCommandWithRoute to queue commands in the transaction
func (t *Transaction) sendCommandWithRoute(
	requestType C.RequestType,
	args []string,
	route config.Route,
) (*C.struct_CommandResponse, error) {
	t.commands = append(t.commands, &GenericCommand{name: requestType, args: args, route: route})
	return nil, nil
}

// Exec executes all queued commands as a transaction
func (t *Transaction) Exec() ([]any, error) {
	result, err := t.executeTransactionCommand(t.commands, false, 0) // Use BaseClient for execution
	if err != nil {
		return nil, err
	}
	return handleAnyArrayResponse(result)
}

// ExecWithOptions executes all queued commands as a transaction with custom options
func (t *Transaction) ExecWithOptions(options *TransactionOption) ([]any, error) {
	var raiseOnError bool
	var timeout uint32

	if options != nil {
		raiseOnError = options.RaiseOnError
		timeout = options.Timeout
	}

	result, err := t.executeTransactionCommand(t.commands, raiseOnError, timeout)
	if err != nil {
		return nil, err
	}
	return handleAnyArrayResponse(result)
}

func (t *Transaction) executeTransactionCommand(
	commands []Cmder,
	raiseOnError bool,
	timeout uint32,
) (*C.CommandResponse, error) {
	return t.executeTransactionWithRoute(commands, nil, raiseOnError, timeout)
}

func (t *Transaction) executeTransactionWithRoute(
	cmds []Cmder,
	route config.Route,
	raiseOnError bool,
	timeout uint32,
) (*C.struct_CommandResponse, error) {
	if len(cmds) == 0 {
		return nil, &errors.RequestError{Msg: "Transaction must contain at least one command"}
	}

	// Convert Go []Cmder to C.Cmder array
	cCmdersArray := C.malloc(C.size_t(len(cmds)) * C.size_t(unsafe.Sizeof(C.Cmder{})))
	defer C.free(cCmdersArray)

	cCmders := (*[1 << 30]C.Cmder)(cCmdersArray)[:len(cmds):len(cmds)]

	for i, cmd := range cmds {
		cArgsArray := C.malloc(C.size_t(len(cmd.Args())) * C.size_t(unsafe.Sizeof(uintptr(0))))
		defer C.free(cArgsArray)

		cArgs := (*[1 << 30]*C.char)(cArgsArray)[:len(cmd.Args()):len(cmd.Args())]

		for j, arg := range cmd.Args() {
			cArgs[j] = C.CString(arg)
		}
		// Check cArgs
		var cArgsPtr **C.char

		if len(cmd.Args()) > 0 {
			if len(cArgs) > 0 {
				cArgsPtr = (**C.char)(unsafe.Pointer(&cArgs[0]))
			}
		}
		cCmders[i] = C.Cmder{
			request_type: C.enum_RequestType(cmd.Name()),
			args_count:   C.uintptr_t(len(cmd.Args())),
			args:         cArgsPtr,
		}
	}
	transaction := C.Transaction{
		cmd_count: C.uintptr_t(len(cmds)),
		commands:  (*C.Cmder)(unsafe.Pointer(cCmdersArray)),
	}

	var routeBytesPtr *C.uchar = nil
	var routeBytesCount C.uintptr_t = 0

	if route != nil && !route.IsMultiNode() {
		routeProto, err := routeToProtobuf(route)
		if err != nil {
			return nil, &errors.RequestError{Msg: "Failed to convert route to protobuf"}
		}
		msg, err := proto.Marshal(routeProto)
		if err != nil {
			return nil, err
		}
		routeBytesCount = C.uintptr_t(len(msg))
		routeBytesPtr = (*C.uchar)(C.CBytes(msg))
		defer C.free(unsafe.Pointer(routeBytesPtr))
	}

	resultChannel := make(chan payload, 1)
	resultChannelPtr := unsafe.Pointer(&resultChannel)

	pinner := pinner{}
	pinnedChannelPtr := uintptr(pinner.Pin(resultChannelPtr))
	defer pinner.Unpin()

	t.mu.Lock()
	if t.coreClient == nil {
		t.mu.Unlock()
		return nil, &errors.ClosingError{Msg: "Transaction failed. The client is closed."}
	}
	t.pending[resultChannelPtr] = struct{}{}

	transactionParam := C.TransactionParam{
		client_adapter_ptr: t.coreClient,
		channel:            C.uintptr_t(pinnedChannelPtr),
		transaction:        &transaction,
		route_bytes:        routeBytesPtr,
		route_bytes_len:    routeBytesCount,
		timeout:            C.uint32_t(timeout),
		raise_on_error:     C.bool(raiseOnError),
	}

	C.execute_transaction(
		transactionParam,
	)

	t.mu.Unlock()

	payload := <-resultChannel

	t.mu.Lock()
	if t.pending != nil {
		delete(t.pending, resultChannelPtr)
	}
	t.mu.Unlock()

	if payload.error != nil {
		return nil, payload.error
	}
	return payload.value, nil
}

// NewTransaction creates a Transaction by embedding the BaseClient
func NewTransaction(client GlideClientCommands) *Transaction {
	glideCli, ok := client.(*GlideClient)
	if !ok {
		panic("client is not of type *glideClient")
	}

	tx := &Transaction{
		baseClient:  glideCli.baseClient, // Access baseClient directly
		GlideClient: glideCli,
		commands:    []Cmder{},
	}

	// Set the executor to the transaction instance itself
	tx.baseClient.executor = tx

	return tx
}

func NewClusterTransaction(client GlideClusterClientCommands) *Transaction {
	glideClusterCli, ok := client.(*GlideClusterClient)
	if !ok {
		panic("client is not of type *glideClusterClient")
	}

	tx := &Transaction{
		baseClient:         glideClusterCli.baseClient, // Access baseClient directly
		GlideClusterClient: glideClusterCli,
		commands:           []Cmder{},
	}

	// Set the executor to the transaction instance itself
	tx.baseClient.executor = tx

	return tx
}

func (client *baseClient) Watch(keys []string) (string, error) {
	result, err := client.executeCommand(C.Watch, keys)
	if err != nil {
		return DefaultStringResponse, err
	}
	return handleOkResponse(result)
}

func (client *baseClient) Unwatch() (string, error) {
	result, err := client.executeCommand(C.UnWatch, []string{})
	if err != nil {
		return DefaultStringResponse, err
	}
	return handleOkResponse(result)
}

func (t *Transaction) Discard() error {
	if len(t.commands) > 0 {
		t.commands = []Cmder{}
		return nil
	}
	return fmt.Errorf("no command where queue")
}
