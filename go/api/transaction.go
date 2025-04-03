package api

// #cgo LDFLAGS: -lglide_ffi
// #cgo !windows LDFLAGS: -lm
// #cgo darwin LDFLAGS: -framework Security
// #cgo darwin,amd64 LDFLAGS: -framework CoreFoundation
// #cgo linux,amd64 LDFLAGS: -L${SRCDIR}/../rustbin/x86_64-unknown-linux-gnu
// #cgo linux,arm64 LDFLAGS: -L${SRCDIR}/../rustbin/aarch64-unknown-linux-gnu
// #cgo darwin,arm64 LDFLAGS: -L${SRCDIR}/../rustbin/aarch64-apple-darwin
// #cgo darwin,amd64 LDFLAGS: -L${SRCDIR}/../rustbin/x86_64-apple-darwin
// #include "../lib.h"
//
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
	commands    []Cmder
}

type Cmder interface {
	Name() C.RequestType
	Args() []string
}

type GenericCommand struct {
	name C.RequestType
	args []string
}

func (cmd *GenericCommand) Name() C.RequestType {
	return cmd.name
}

func (cmd *GenericCommand) Args() []string {
	return cmd.args
}

func NewMultiCommand() Cmder {
	return &GenericCommand{name: C.Multi, args: []string{}}
}

func NewExecCommand() Cmder {
	return &GenericCommand{name: C.Exec, args: []string{}}
}

// Override ExecuteCommand to queue commands in the transaction
func (t *Transaction) ExecuteCommand(requestType C.RequestType, args []string) (*C.struct_CommandResponse, error) {
	fmt.Println("Transaction ExecuteCommand called")
	t.commands = append(t.commands, &GenericCommand{name: requestType, args: args})
	return nil, nil // Queue the command instead of executing immediately
}

// Exec executes all queued commands as a transaction
func (t *Transaction) Exec() error {
	// Add MULTI and EXEC to the command queue
	//t.commands = append([]Cmder{NewMultiCommand()}, t.commands...)
	// t.commands = append(t.commands, &GenericCommand{C.Get, []string{"apples"}})
	//t.commands = append(t.commands, NewExecCommand())

	// Execute all commands
	result, _ := t.baseClient.executeTransactionCommand(t.commands) // Use BaseClient for execution
	fmt.Println(handleAnyArrayResponse(result))
	return nil
}

func (client *baseClient) executeTransactionCommand(commands []Cmder) (*C.CommandResponse, error) {
	return client.ExecuteTransaction(commands, nil)
}

func (client *baseClient) ExecuteTransaction(cmds []Cmder, route config.Route) (*C.struct_CommandResponse, error) {
	if len(cmds) == 0 {
		return nil, &errors.RequestError{Msg: "Transaction must contain at least one command"}
	}

	// Convert Go []Cmder to C.Cmder array
	cCmdersArray := C.malloc(C.size_t(len(cmds)) * C.size_t(unsafe.Sizeof(C.Cmder{})))
	defer C.free(cCmdersArray)

	cCmders := (*[1 << 30]C.Cmder)(cCmdersArray)[:len(cmds):len(cmds)]

	argPtrs := []*C.char{} // Track allocated memory for cleanup

	for i, cmd := range cmds {
		cArgsArray := C.malloc(C.size_t(len(cmd.Args())) * C.size_t(unsafe.Sizeof(uintptr(0))))
		defer C.free(cArgsArray)

		cArgs := (*[1 << 30]*C.char)(cArgsArray)[:len(cmd.Args()):len(cmd.Args())]

		for j, arg := range cmd.Args() {
			cArgs[j] = C.CString(arg)
			argPtrs = append(argPtrs, cArgs[j])
		}

		cCmders[i] = C.Cmder{
			request_type: C.enum_RequestType(cmd.Name()),
			args_count:   C.uintptr_t(len(cmd.Args())),
			args:         (**C.char)(unsafe.Pointer(&cArgs[0])),
		}
	}

	transaction := C.Transaction{
		cmd_count: C.uintptr_t(len(cmds)),
		commands:  (*C.Cmder)(unsafe.Pointer(cCmdersArray)),
	}

	var routeBytesPtr *C.uchar = nil
	var routeBytesCount C.uintptr_t = 0

	if route != nil {
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

	client.mu.Lock()
	if client.coreClient == nil {
		client.mu.Unlock()
		return nil, &errors.ClosingError{Msg: "Transaction failed. The client is closed."}
	}
	client.pending[resultChannelPtr] = struct{}{}

	fmt.Println("Before execute_transaction")
	C.execute_transaction(
		client.coreClient,
		C.uintptr_t(pinnedChannelPtr),
		&transaction,
		routeBytesPtr,
		routeBytesCount,
	)
	fmt.Println("After execute_transaction")

	client.mu.Unlock()

	payload := <-resultChannel

	client.mu.Lock()
	if client.pending != nil {
		delete(client.pending, resultChannelPtr)
	}
	client.mu.Unlock()

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
		baseClient: glideCli.baseClient, // Access baseClient directly
		commands:   []Cmder{},
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
	return handleStringResponse(result)
}

func (t *Transaction) Discard() error {
	if len(t.commands) > 0 {
		t.commands = []Cmder{}
		return nil
	}
	return fmt.Errorf("no command where queue")
}

// func (client *Transaction) Set(key string, value string) interface{} {
// 	fmt.Println("Transaction Set!")
// 	_, err := client.ExecuteCommand(C.Set, []string{key, value})
// 	if err != nil {
// 		return nil
// 	}

// 	return client
// }

// func (client *Transaction) Get(key string) interface{} {
// 	_, err := client.ExecuteCommand(C.Get, []string{key})

// 	if err != nil {
// 		return nil
// 	}
// 	return client
// }
