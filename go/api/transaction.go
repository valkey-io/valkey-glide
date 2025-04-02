package api

// #cgo LDFLAGS: -L../target/release -lglide_rs
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
	//result, err := t.baseClient.ExecuteCommand(cmd.Name(), cmd.Args()) // Use BaseClient for execution
	result, _ := t.baseClient.executeTransactionCommand(t.commands) // Use BaseClient for execution
	fmt.Println(handleStringResponse(result))
	// fmt.Println("Final:", result)
	// fmt.Println(err)
	// for _, cmd := range t.commands {
	// 	result, err := t.baseClient.ExecuteCommand(cmd.Name(), cmd.Args())
	// 	fmt.Println(result) // Use BaseClient for execution
	// 	//_, err := t.baseClient.executeTransactionCommandWithRoute(t.commands, nil) // Use BaseClient for execution
	// 	if err != nil {
	// 		return fmt.Errorf("failed to execute command %s: %w", cmd.Name(), err)
	// 	}
	// }
	return nil
}

func (client *baseClient) executeTransactionCommand(commands []Cmder) (*C.struct_CommandResponse, error) {
	return client.executeTransactionCommandWithRoute(commands, nil)
}

func (client *baseClient) executeTransactionCommandWithRoute(
	cmds []Cmder,
	route config.Route,
) (*C.struct_CommandResponse, error) {

	if len(cmds) == 0 {
		return nil, &errors.RequestError{Msg: "Transaction must contain at least one command"}
	}

	// Convert Go []Cmder to C.Cmder array
	cCmders := make([]C.Cmder, len(cmds))
	argPtrs := make([]*C.char, 0)

	for i, cmd := range cmds {
		// Convert command arguments to C strings
		cArgs := make([]*C.char, len(cmd.Args()))
		for j, arg := range cmd.Args() {
			cArgs[j] = C.CString(arg)
			argPtrs = append(argPtrs, cArgs[j]) // Keep track for cleanup
		}

		cCmders[i] = C.Cmder{
			request_type: uint32(cmd.Name()),
			args_count:   C.uintptr_t(len(cmd.Args())),
			args:         (**C.char)(unsafe.Pointer(&cArgs[0])),
		}
	}

	// Construct Transaction
	transaction := C.Transaction{
		cmd_count: C.uintptr_t(len(cmds)),
		commands:  (*C.Cmder)(unsafe.Pointer(&cCmders[0])),
	}

	// Convert route to protobuf if provided
	var routeBytesPtr *C.uchar = nil
	var routeBytesCount C.size_t = 0
	if route != nil {
		routeProto, err := routeToProtobuf(route)
		if err != nil {
			return nil, fmt.Errorf("Failed to convert route to protobuf: %v", err)
		}
		msg, err := proto.Marshal(routeProto)
		if err != nil {
			return nil, err
		}

		routeBytesCount = C.size_t(len(msg))
		routeBytesPtr = (*C.uchar)(C.CBytes(msg))
	}

	// Call Rust FFI function with route parameter
	fmt.Println("Before C.execute_transaction")
	resp := C.execute_transaction(client.coreClient, 0, &transaction, routeBytesPtr, routeBytesCount)
	fmt.Println("Before C.execute_transaction", resp)
	// Free C strings
	for _, ptr := range argPtrs {
		C.free(unsafe.Pointer(ptr))
	}

	if resp == nil {
		return nil, &errors.RequestError{Msg: "Transaction execution failed"}
	}

	return nil, nil
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

// // NewGlideClient creates a [GlideClientCommands] in standalone mode using the given [GlideClientConfiguration].
// func NewGlideClient(config *GlideClientConfiguration) (GlideClientCommands, error) {
// 	client, err := createClient(config)
// 	if err != nil {
// 		return nil, err
// 	}

// 	return &GlideClient{client}, nil
// }

func (client *baseClient) Watch(keys []string) (string, error) {
	result, err := client.executeCommand(C.Watch, keys)
	if err != nil {
		return DefaultStringResponse, err
	}
	return handleStringResponse(result)
}

// func (client *baseClient) Discard(keys []string) (string, error) {
// 	result, err := client.executeCommand(C.Watch, keys)
// 	if err != nil {
// 		return DefaultStringResponse, err
// 	}
// 	return handleStringResponse(result)
// }

func (t *Transaction) Discard() error {
	if len(t.commands) > 0 {
		t.commands = []Cmder{}
		return nil
	}
	return fmt.Errorf("no command where queue")
}
