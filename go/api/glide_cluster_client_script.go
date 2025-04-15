// InvokeScript executes a Lua script on the server.
//
// Parameters:
//
//	script - The script to execute.
//
// Return value:
//
//	The result of the script execution.
//
// See [valkey.io] for details.
//
// [valkey.io]: https://valkey.io/commands/eval/
func (client *GlideClusterClient) InvokeScript(script *options.Script) (ClusterValue[any], error) {
	return client.InvokeScriptWithRoute(script, options.RouteOption{})
}

// InvokeScriptWithOptions executes a Lua script on the server with additional options.
//
// Parameters:
//
//	script - The script to execute.
//	scriptOptions - Options for script execution including keys and arguments.
//
// Return value:
//
//	The result of the script execution.
//
// See [valkey.io] for details.
//
// [valkey.io]: https://valkey.io/commands/eval/
func (client *GlideClusterClient) InvokeScriptWithOptions(script *options.Script, scriptOptions *options.ScriptOptions) (ClusterValue[any], error) {
	return client.InvokeScriptWithOptionsAndRoute(script, scriptOptions, options.RouteOption{})
}

// InvokeScriptWithRoute executes a Lua script on the server with routing information.
//
// Parameters:
//
//	script - The script to execute.
//	route - Routing information for the script execution.
//
// Return value:
//
//	The result of the script execution.
//
// See [valkey.io] for details.
//
// [valkey.io]: https://valkey.io/commands/eval/
func (client *GlideClusterClient) InvokeScriptWithRoute(script *options.Script, route options.RouteOption) (ClusterValue[any], error) {
	response, err := client.baseClient.executeScriptWithRoute(script.GetHash(), []string{}, []string{}, route.Route)
	if err != nil {
		return createEmptyClusterValue[any](), err
	}
	
	result, err := handleCommandResponse(response)
	if err != nil {
		return createEmptyClusterValue[any](), err
	}
	
	if route.Route != nil && route.Route.IsMultiNode() {
		data, err := handleStringToAnyMapResponse(result)
		if err != nil {
			return createEmptyClusterValue[any](), err
		}
		return createClusterMultiValue[any](data), nil
	}
	
	return createClusterSingleValue[any](result), nil
}

// InvokeScriptWithOptionsAndRoute executes a Lua script on the server with additional options and routing information.
//
// Parameters:
//
//	script - The script to execute.
//	scriptOptions - Options for script execution including keys and arguments.
//	route - Routing information for the script execution.
//
// Return value:
//
//	The result of the script execution.
//
// See [valkey.io] for details.
//
// [valkey.io]: https://valkey.io/commands/eval/
func (client *GlideClusterClient) InvokeScriptWithOptionsAndRoute(script *options.Script, scriptOptions *options.ScriptOptions, route options.RouteOption) (ClusterValue[any], error) {
	keys := scriptOptions.GetKeys()
	args := scriptOptions.GetArgs()
	
	response, err := client.baseClient.executeScriptWithRoute(script.GetHash(), keys, args, route.Route)
	if err != nil {
		return createEmptyClusterValue[any](), err
	}
	
	result, err := handleCommandResponse(response)
	if err != nil {
		return createEmptyClusterValue[any](), err
	}
	
	if route.Route != nil && route.Route.IsMultiNode() {
		data, err := handleStringToAnyMapResponse(result)
		if err != nil {
			return createEmptyClusterValue[any](), err
		}
		return createClusterMultiValue[any](data), nil
	}
	
	return createClusterSingleValue[any](result), nil
}
