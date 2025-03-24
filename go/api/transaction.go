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
	t.commands = append([]Cmder{NewMultiCommand()}, t.commands...)
	// t.commands = append(t.commands, &GenericCommand{C.Get, []string{"apples"}})
	// t.commands = append(t.commands, NewExecCommand())

	// Execute all commands
	for _, cmd := range t.commands {
		_, err := t.baseClient.ExecuteCommand(cmd.Name(), cmd.Args()) // Use BaseClient for execution
		if err != nil {
			return fmt.Errorf("failed to execute command %s: %w", cmd.Name(), err)
		}
	}
	return nil
}

// NewTransaction creates a Transaction by embedding the BaseClient
func NewTransaction(client BaseClient) *Transaction {
	glideCli, ok := client.(*glideClient)
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
