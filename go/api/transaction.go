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
	// t.commands = append(t.commands, NewExecCommand())

	// Execute all commands
	result, err := t.baseClient.executeTransactionCommandWithRoute(t.commands, nil) // Use BaseClient for execution
	fmt.Println(result)
	fmt.Println(err)
	// for _, cmd := range t.commands {
	// 	_, err := t.baseClient.executeTransactionCommandWithRoute(t.commands, nil) // Use BaseClient for execution
	// 	if err != nil {
	// 		return fmt.Errorf("failed to execute command %s: %w", cmd.Name(), err)
	// 	}
	// }
	return nil
}

func (client *baseClient) executeTransactionCommandWithRoute(
	commands []Cmder,
	route config.Route,
) (*C.struct_CommandResponse, error) {

	var routeBytesPtr *C.uchar = nil
	var routeBytesCount C.uintptr_t = 0
	if route != nil {
		routeProto, err := routeToProtobuf(route)
		if err != nil {
			return nil, &errors.RequestError{Msg: "ExecuteCommand failed due to invalid route"}
		}
		msg, err := proto.Marshal(routeProto)
		if err != nil {
			return nil, err
		}

		routeBytesCount = C.uintptr_t(len(msg))
		routeBytesPtr = (*C.uchar)(C.CBytes(msg))
	}

	// make the channel buffered, so that we don't need to acquire the client.mu in the successCallback and failureCallback.
	resultChannel := make(chan payload, 1)
	resultChannelPtr := unsafe.Pointer(&resultChannel)

	pinner := pinner{}
	pinnedChannelPtr := uintptr(pinner.Pin(resultChannelPtr))
	defer pinner.Unpin()

	client.mu.Lock()
	if client.coreClient == nil {
		client.mu.Unlock()
		return nil, &errors.ClosingError{Msg: "ExecuteCommand failed. The client is closed."}
	}
	client.pending[resultChannelPtr] = struct{}{}
	client.mu.Unlock()
	for _, cmd := range commands {
		var cArgsPtr *C.uintptr_t = nil
		var argLengthsPtr *C.ulong = nil
		if len(cmd.Args()) > 0 {
			cArgs, argLengths := toCStrings(cmd.Args())
			cArgsPtr = &cArgs[0]
			argLengthsPtr = &argLengths[0]
		}

		C.command(
			client.coreClient,
			C.uintptr_t(pinnedChannelPtr),
			uint32(cmd.Name()),
			C.size_t(len(cmd.Args())),
			cArgsPtr,
			argLengthsPtr,
			routeBytesPtr,
			routeBytesCount,
		)
		payload := <-resultChannel
		fmt.Println("payload: ", payload.value)
	}

	payload := <-resultChannel

	client.mu.Lock()
	if client.pending != nil {
		delete(client.pending, resultChannelPtr)
	}
	client.mu.Unlock()

	if payload.error != nil {
		return nil, payload.error
	}
	fmt.Println("payload1: ", payload.value)
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
