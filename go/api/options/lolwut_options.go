// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 

package options
 
import (
	"github.com/valkey-io/valkey-glide/go/utils"
)
 
type LolwutOptions struct {
	Version int64
	Args    *[]int
}
 
type ClusterLolwutOptions struct {
	*LolwutOptions
	// Specifies the routing configuration for the command.
	// The client will route the command to the nodes defined by *Route*.
	*RouteOption
}
 
func NewLolwutOptions(version int64) *LolwutOptions {
	return &LolwutOptions{Version: version}
}
 
func (options *LolwutOptions) SetArgs(args []int) *LolwutOptions {
	options.Args = &args
	return options
}
 
func (options *LolwutOptions) ToArgs() ([]string, error) {
	if options == nil {
		return []string{}, nil
	}
	//var err error
	args := []string{"VERSION", utils.IntToString(options.Version)}
 
	if options.Args != nil {
		for _, arg := range *options.Args {
			args = append(args, utils.IntToString(int64(arg)))
		}
	}
 
	return args, nil
}
