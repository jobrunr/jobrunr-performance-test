#!/usr/bin/env bash
# ---------------------------------------------------------------------------
# run-scenario.sh
#
# One-file helper for performance tests + autocompletion.
#
# Usage (recommended for devs):
#   # enable command + autocomplete in current shell
#   source ./run-scenario.sh
#
#   # then use:
#   run-scenario JobRunrPro <TAB> ...
#
# Direct execution (CI / scripts):
#   ./run-scenario.sh JobRunrPro Scenario00CombinedScenario Postgres 500000
# ---------------------------------------------------------------------------

# ========== detection: sourced vs executed ==========

is_sourced() {
  # Bash
  if [ -n "${BASH_VERSION-}" ]; then
    [[ "${BASH_SOURCE[0]}" != "$0" ]] && return 0 || return 1
  fi
  # Zsh
  if [ -n "${ZSH_VERSION-}" ]; then
    case $ZSH_EVAL_CONTEXT in
      *:file:*) return 0 ;;
    esac
  fi
  return 1
}

# ========== resolve script path (for both modes) ==========

if [ -n "${BASH_SOURCE-}" ]; then
  SCRIPT_PATH="${BASH_SOURCE[0]}"
elif [ -n "${ZSH_VERSION-}" ]; then
  # zsh when sourced
  SCRIPT_PATH="${(%):-%x}"
else
  SCRIPT_PATH="$0"
fi

SCRIPT_DIR="$(cd "$(dirname "$SCRIPT_PATH")" && pwd)"

if ROOT_GIT=$(cd "$SCRIPT_DIR" && git rev-parse --show-toplevel 2>/dev/null); then
  ROOT_DIR="$ROOT_GIT"
else
  ROOT_DIR="$SCRIPT_DIR"
fi

# ========== config ==========

TOOLS=("JobRunr" "JobRunrPro" "Quartz")
DATABASES=("DB2" "MariaDB" "MySQL" "Oracle" "Postgres" "SQLServer" "MongoDB")
DEFAULT_AMOUNT=500000

default_tool_version() {
  # Only used for completion suggestion, not enforced.
  case "$1" in
    JobRunr) echo "1.0.0-SNAPSHOT" ;;
    JobRunrPro) echo "1.0.0-SNAPSHOT" ;;
    Quartz)     echo "2.5.1-SNAPSHOT" ;;
    *)          echo "" ;;
  esac
}

database_to_datastore_class() {
  # Map logical DB name -> datastore class (case-insensitive)
  local db
  db="$(printf '%s' "$1" | tr '[:upper:]' '[:lower:]')"
  case "$db" in
    postgres)      echo "PostgresDataStore" ;;
    mariadb|mysql) echo "MySqlDataStore" ;;
    mongodb)       echo "MongoDBDataStore" ;;
    oracle)        echo "OracleDataStore" ;;
    sqlserver)     echo "SqlServerDataStore" ;;
    db2)           echo "Db2DataStore" ;;
    *)             echo "Unknown" ;;
  esac
}

list_scenarios() {
  local tool="$1"
  local base=""

  case "$tool" in
    JobRunrPro)
      base="$ROOT_DIR/jobrunr-pro/src/main/java/org/jobrunrpro/performance/scenario"
      ;;
    JobRunrOSS|JobRunr)
      base="$ROOT_DIR/jobrunr/src/main/java/org/jobrunr/performance/scenario"
      ;;
    Quartz)
      base="$ROOT_DIR/quartz/src/main/java/org/quartz/performance/scenario"
      ;;
    *)
      # Unknown tool: no scenarios
      return 0
      ;;
  esac

  if [[ -d "$base" ]]; then
    find "$base" -type f -name 'Scenario*.java' 2>/dev/null \
      | sed 's:.*/::' \
      | sed 's/\.java$//' \
      | sort -u
  fi
}

# ========== usage ==========

print_usage() {
  cat <<EOF
Usage:
  run-scenario <tool> [scenario] [database] [amount] [key=value]...

Tools:
  ${TOOLS[*]}

Databases:
  ${DATABASES[*]}

Notes:
  - toolVersion is OPTIONAL:
      If omitted, no -Djobrunr.version / -Dquartz.version is passed.
      Maven will use the version defined in pom.xml.
  - Only JobRunrPro currently has multiple scenarios; these are autocompleted.

Examples:
  run-scenario JobRunrPro Scenario00CombinedScenario Postgres 500000
  run-scenario JobRunrPro Scenario01ProcessJobs MySQL 1000000
  run-scenario JobRunrPro Scenario01ProcessJobs Postgres 500000 toolVersion=1.0.0-SNAPSHOT
  run-scenario JobRunrOSS Scenario01ProcessJobs Postgres 200000
  run-scenario Quartz Scenario01ProcessJobs Oracle 100000 toolVersion=2.5.1

Install using:
  - 'source ./run-scenario.sh':
      run-scenario <TAB>                 → tools
      run-scenario JobRunrPro <TAB>      → scenarios
      run-scenario ... <TAB>             → databases, amounts, toolVersion=

EOF
}

# ========== main run logic ==========

_run_scenario_exec() {
  # This runs in a subshell when called as standalone script.
  set -euo pipefail

  local tool="${1:-}"
  local scenario="${2:-}"
  local database="${3:-}"
  local amount="${4:-}"

  # Collect remaining args as extra key=value pairs
  local -a extra_args=()
  if (($# > 4)); then
    shift 4
    extra_args=("$@")
  else
    shift "$#"
  fi

  if [[ -z "$tool" ]]; then
    print_usage
    exit 1
  fi

  # Extract optional toolVersion=... from extra_args
  local tool_version=""
  local -a passthrough=()
  if ((${#extra_args[@]} > 0)); then
    for kv in "${extra_args[@]}"; do
      if [[ "$kv" == toolVersion=* ]]; then
        tool_version="${kv#toolVersion=}"
      else
        passthrough+=("$kv")
      fi
    done
  fi

  [[ -z "$amount" ]] && amount="$DEFAULT_AMOUNT"

  local datastore_class="Unknown"
  if [[ -n "${database:-}" ]]; then
    datastore_class="$(database_to_datastore_class "$database")"
  fi

  local exec_args="tool=${tool} amount=${amount}"
  [[ -n "$scenario" ]] && exec_args+=" scenario=${scenario}"
  [[ "$datastore_class" != "Unknown" ]] && exec_args+=" datastore=${datastore_class}"

  if ((${#passthrough[@]} > 0)); then
    exec_args+=" ${passthrough[*]}"
  fi

  echo "Running:"
  echo "  Tool       : $tool"
  [[ -n "$scenario" ]] && echo "  Scenario   : $scenario"
  [[ -n "$database" ]] && echo "  Database   : $database ($datastore_class)"
  echo "  Amount     : $amount"
  if [[ -n "$tool_version" ]]; then
    echo "  ToolVersion: $tool_version (explicit)"
  else
    echo "  ToolVersion: (using pom.xml default)"
  fi
  echo

  local mvn_cmd=(mvn -f "$ROOT_DIR/start/pom.xml" -P $tool compile exec:java@performance-test)

  # Only add version flag if explicitly provided
  if [[ -n "$tool_version" ]]; then
    mvn_cmd+=("-tool.version=$tool_version")
  fi

  mvn_cmd+=("-Dexec.args=$exec_args")

  printf 'Command:\n  '
  printf '%q ' "${mvn_cmd[@]}"
  printf '\n\n'

  mvn clean install -DskipTests
  time "${mvn_cmd[@]}"
}

# ========== completion ==========

_run_scenario_complete() {
  COMPREPLY=()

  # Use bash-style completion (works in bash & zsh with bashcompinit)
  local cur="${COMP_WORDS[COMP_CWORD]}"
  local cword="$COMP_CWORD"
  local tool="${COMP_WORDS[1]:-}"

  case "$cword" in
    1)
      # complete tool
      COMPREPLY=( $(compgen -W "${TOOLS[*]}" -- "$cur") )
      ;;
    2)
      # complete scenario, filtered by tool
      if [[ -n "$tool" ]]; then
        local scenarios
        scenarios="$(list_scenarios "$tool")"
        if [[ -n "$scenarios" ]]; then
          COMPREPLY=( $(compgen -W "$scenarios" -- "$cur") )
        fi
      fi
      ;;
    3)
      # complete database
      COMPREPLY=( $(compgen -W "${DATABASES[*]}" -- "$cur") )
      ;;
    4)
      # complete amount
      COMPREPLY=( $(compgen -W "10000 50000 100000 500000 1000000" -- "$cur") )
      ;;
    *)
      # complete toolVersion=...
      if [[ "$cur" == toolVersion=* ]]; then
        local def
        def="$(default_tool_version "$tool")"
        if [[ -n "$def" ]]; then
          COMPREPLY=( "toolVersion=$def" )
        else
          COMPREPLY=( "toolVersion=" )
        fi
      fi
      ;;
  esac

  return 0
}

_setup_run_scenario_completion() {
  if [[ -n "${ZSH_VERSION-}" ]]; then
    autoload -U +X bashcompinit 2>/dev/null || true
    bashcompinit 2>/dev/null || true
  fi

  # 👇 Add this line — makes completion ignore case (bash & zsh w/ bashcompinit)
  shopt -s nocaseglob 2>/dev/null || true
  bind 'set completion-ignore-case on' 2>/dev/null || true

  if type complete &>/dev/null; then
    complete -F _run_scenario_complete run-scenario 2>/dev/null || true
  fi
}

# ========== sourced vs executed behavior ==========

if is_sourced; then
  # When sourced:
  # - Define `run-scenario` function that calls this file.
  # - Register completion for `run-scenario`.
  run-scenario() {
    "$SCRIPT_PATH" "$@"
  }
  _setup_run_scenario_completion
else
  # When executed directly: just run.
  if [ $# -eq 0 ]; then
    print_usage
    exit 1
  fi
  _run_scenario_exec "$@"
fi
