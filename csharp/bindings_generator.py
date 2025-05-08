import argparse
import json
import os
from os.path import join

RUST_CODE_PATH = './ffi/src/lib.rs'

# C# template for DllImport and RequestType enum entries
C_SHARP_DllImport_TEMPLATE = """
    [DllImport("libglide_rs", CallingConvention = CallingConvention.Cdecl, EntryPoint = "{func_name}")]
    private static extern long {func_name}({params});
"""

C_SHARP_REQUESTTYPE_TEMPLATE = """
    {enum_name} = {enum_value}, // {description}
"""

def generate_bindings_and_enum(functions):
    """
    This function generates C# bindings for Rust FFI functions and creates an enum for RequestType.
    It takes a list of function definitions, extracts the function names and parameters, and
    generates corresponding RequestType enum entries and DllImport statements for each Rust function.

    Args:
    functions (list): A list of tuples containing function names, parameters, and return types.

    Generates a C# file 'generated_bindings.cs' containing the RequestType enum and DllImport bindings.
    """
    request_type_enum = []
    csharp_imports = []

    for i, (func_name, params_str, return_type) in enumerate(functions, start=1500):
        # Generate the RequestType enum entry for each function
        enum_value = i
        description = f"Request Type for {func_name.upper()} command"
        enum_entry = C_SHARP_REQUESTTYPE_TEMPLATE.format(
            enum_name=func_name.capitalize(),
            enum_value=enum_value,
            description=description
        )
        request_type_enum.append(enum_entry)

        # Generate the DllImport statement for each function
        params = ', '.join([f"IntPtr {param.strip()}" for param in params_str.split(',') if param.strip()])
        dll_import = C_SHARP_DllImport_TEMPLATE.format(
            func_name=func_name,
            params=params
        )
        csharp_imports.append(dll_import)

    enum_code = "\n".join(request_type_enum)
    import_code = "\n".join(csharp_imports)

    with open('generated_bindings.cs', 'w') as file:
        file.write(f"""
// Generated C# Bindings for Rust FFI functions

namespace Valkey.Glide
{{
    internal enum RequestType : uint
    {{
        {enum_code}
    }}

    // FFI Bindings
    public static class FfiBindings
    {{
        {import_code}
    }}
}}
""")
    print("Bindings and enum generation complete!")

def main():
    """
    Main function that parses the input JSON files containing command information,
    categorizes commands based on their routing policy, and generates FFI bindings
    for supported commands like COPY and DEL. It also prints categorized commands.
    """
    parser = argparse.ArgumentParser(
        description="Analyzes command info json and categorizes commands into their RouteBy categories"
    )
    parser.add_argument(
        "--commands-dir",
        type=str,
        help="Path to the directory containing the command info json files (example: ../../valkey/src/commands)",
        required=True,
    )

    args = parser.parse_args()
    commands_dir = args.commands_dir
    if not os.path.exists(commands_dir):
        raise parser.error(
            "The command info directory passed to the '--commands-dir' argument does not exist"
        )

    # Categories for command routing
    all_nodes = CommandCategory("AllNodes", "Commands with an ALL_NODES request policy")
    all_primaries = CommandCategory(
        "AllPrimaries", "Commands with an ALL_SHARDS request policy"
    )
    uncategorized = CommandCategory(
        "Uncategorized",
        "Commands that don't fall into the other categories. These commands will have to be manually categorized.",
    )

    categories = [
        all_nodes,
        all_primaries,
        uncategorized,
    ]

    print("Gathering command info...\n")

    # This list will hold the Rust functions for which we want to generate C# bindings
    functions = []

    # Process each command info json file in the provided directory
    for filename in os.listdir(commands_dir):
        file_path = join(commands_dir, filename)
        _, file_extension = os.path.splitext(file_path)
        if file_extension != ".json":
            print(f"Note: {filename} is not a json file and will thus be ignored")
            continue

        file = open(file_path)
        command_json = json.load(file)
        if len(command_json) == 0:
            raise Exception(
                f"Command json for {filename} was empty. A json object with information about the command was expected."
            )

        command_name = next(iter(command_json))
        command_info = command_json[command_name]

        # If the command has a container (e.g., 'XINFO GROUPS'), handle it
        if "container" in command_info:
            command_name = f"{command_info['container']} {command_name}"

        # Categorize commands based on their routing policy
        if "command_tips" in command_info:
            request_policy = get_request_policy(command_info["command_tips"])
            if request_policy == "ALL_NODES":
                all_nodes.add_command(command_name)
            elif request_policy == "ALL_SHARDS":
                all_primaries.add_command(command_name)

        # If the command is recognized (e.g., COPY or DEL), add to functions list
        if command_name.lower() in ["copy", "del"]:
            functions.append((command_name, "source, destination", "i64"))

        uncategorized.add_command(command_name)

    generate_bindings_and_enum(functions)

    print("\nCategorizing commands...")
    for category in categories:
        print_category(category)

def get_request_policy(command_tips):
    """
    Extracts the request policy (e.g., ALL_NODES, ALL_SHARDS) from command tips.

    Args:
    command_tips (list): A list of command tips that may contain a REQUEST_POLICY.

    Returns:
    str: The request policy string if found, otherwise None.
    """
    for command_tip in command_tips:
        if command_tip.startswith("REQUEST_POLICY:"):
            return command_tip[len("REQUEST_POLICY:") :]
    return None

def print_category(category):
    """
    Prints the categorized commands along with their descriptions.
    """
    print("============================")
    print(f"Category: {category.name} commands")
    print(f"Description: {category.description}")
    print("List of commands in this category:\n")

    if len(category.commands) == 0:
        print("(No commands found for this category)")
    else:
        category.commands.sort()
        for command_name in category.commands:
            print(f"{command_name}")

    print("\n")

# represent command categories
class CommandCategory:
    def __init__(self, name, description):
        """Initializes a new category for organizing commands."""
        self.name = name
        self.description = description
        self.commands = []

    def add_command(self, command_name):
        """Adds a command to the category."""
        self.commands.append(command_name)

if __name__ == "__main__":
    main()
