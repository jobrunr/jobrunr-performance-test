#!/bin/bash
# =============================================================================
# my_script.sh - A Maven launcher with autocompletion for datastore and scenario.
#
# Usage (when executing):
#   ./my_script.sh <type> [version] <DataStore> <scenario> [optional args...]
#
# Example:
#   ./my_script.sh Pro v7.4.0 PostgresDataStore Scenario01ProcessJobs amount=500_000 dashboard_port=8010
#
# When sourced, this script registers a custom completion function for its name.
# =============================================================================

# --- Autocompletion Code ---
# This block is executed only when the script is sourced.
if [[ "${BASH_SOURCE[0]}" != "${0}" ]]; then
    _my_script_complete() {
        local cur
        # Initialize the current word to complete.
        cur="${COMP_WORDS[COMP_CWORD]}"

        # For argument 3 (index 3, since index starts at 0)
        if [ $COMP_CWORD -eq 3 ]; then
            COMPREPLY=( $(compgen -W "PostgresDataStore MySQLDataStore MongoDataStore OracleDataStore" -- "$cur") )
            return 0
        fi

        # For argument 4 (index 4)
        if [ $COMP_CWORD -eq 4 ]; then
            COMPREPLY=( $(compgen -W "Scenario01ProcessJobs Scenario02DataImport Scenario03BatchProcess" -- "$cur") )
            return 0
        fi
    }

    # Register the completion function for this script's name.
    complete -F _my_script_complete "$(basename "${BASH_SOURCE[0]}")"
    return 0
fi

# --- End Autocompletion Code ---

# --- Main Script Execution ---

# Check for at least 4 arguments.
if [[ $# -lt 4 ]]; then
    echo "Usage: $0 <type (Oss|Pro)> [version (default: 1.0.0-SNAPSHOT)] <DataStore> <scenario> [optional args...]"
    exit 1
fi

# Parse arguments.
TYPE="$1"
VERSION="${2:-1.0.0-SNAPSHOT}"
DATASTORE="$3"
SCENARIO="$4"
shift 4
EXTRA_ARGS="$*"

# If version starts with 'v', strip it (e.g., v7.4.0 -> 7.4.0).
VERSION="${VERSION#v}"

# Convert TYPE to uppercase for the Maven profile.
TYPE_UPPER=$(echo "$TYPE" | tr '[:lower:]' '[:upper:]')

# Compute the project root.
# Assuming the script is located in <project_root>/run/oss, go up two levels.
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(dirname "$(dirname "$SCRIPT_DIR")")"

echo "Using pom.xml from: $PROJECT_ROOT/pom.xml"

# Build the Maven command.
MVN_CMD="time mvn -f \"$PROJECT_ROOT/pom.xml\" compile exec:java -P $TYPE_UPPER -Djobrunr.version=$VERSION -Dexec.args=\"datastore=$DATASTORE scenario=$SCENARIO $EXTRA_ARGS\""

echo "Running command:"
echo "$MVN_CMD"

# Execute the Maven command.
eval $MVN_CMD