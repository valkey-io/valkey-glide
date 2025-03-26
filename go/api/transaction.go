package api

// #cgo LDFLAGS: -L../target/release -lglide_rs
// #include "../lib.h"
//
// void successCallback(void *channelPtr, struct CommandResponse *message);
// void failureCallback(void *channelPtr, char *errMessage, RequestErrorType errType);
import "C"

import (
	"fmt"
)

// BaseClient defines an interface for methods common to both [GlideClientCommands] and [GlideClusterClientCommands].
type TransactionClient interface {
	TransactionBaseCommands
	// Close terminates the client by closing all associated resources.
	Close()
}
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
	fmt.Println("ExecuteCommand Param: ", requestType, args)
	fmt.Println("t.commands Before: ", t.commands)
	t.commands = append(t.commands, &GenericCommand{name: requestType, args: args})
	//fmt.Println("t.commands After: ", t.commands)

	return nil, nil // Queue the command instead of executing immediately
}

// Exec executes all queued commands as a transaction
func (t *Transaction) Exec() error {
	// Add MULTI and EXEC to the command queue

	// t.commands = append(t.commands, &GenericCommand{C.Get, []string{"apples"}})
	t.commands = append([]Cmder{NewMultiCommand()}, t.commands...)
	t.commands = append(t.commands, NewExecCommand())

	for i, cmd := range t.commands {
		fmt.Println("CommandList:", i, cmd.Name(), cmd.Args())
	}
	// Execute all commands
	for i, cmd := range t.commands {
		fmt.Println("Exec Command:", i, cmd.Name(), cmd.Args())
		result, err := t.baseClient.ExecuteCommand(cmd.Name(), cmd.Args()) // Use BaseClient for execution
		fmt.Println(result)
		if err != nil {
			return fmt.Errorf("failed to execute command %s: %w", cmd.Name(), err)
		}
	}
	return nil
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
