#!/bin/sh

set -eu

script_directory=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd) || {
    echo "Unable to resolve the launcher directory." >&2
    exit 1
}
repository_root=$(CDPATH= cd -- "$script_directory/.." && pwd) || {
    echo "Unable to resolve the repository root." >&2
    exit 1
}

usage() {
    echo "Usage: $0 <identity-service|school-service|academic-service> [--check] [Maven arguments...]" >&2
    exit 2
}

[ "$#" -ge 1 ] || usage
service_name=$1
shift

case "$service_name" in
    identity-service)
        service_port=8081
        required_variables="IDENTITY_DB_PASSWORD IDENTITY_INTERNAL_API_KEY"
        ;;
    school-service)
        service_port=8080
        required_variables="SCHOOL_SERVICE_DB_PASSWORD IDENTITY_INTERNAL_API_KEY SCHOOL_INTERNAL_API_KEY ACADEMIC_INTERNAL_API_KEY"
        ;;
    academic-service)
        service_port=8082
        required_variables="ACADEMIC_SERVICE_DB_PASSWORD IDENTITY_INTERNAL_API_KEY SCHOOL_INTERNAL_API_KEY ACADEMIC_INTERNAL_API_KEY"
        ;;
    *)
        echo "Unsupported service: $service_name" >&2
        usage
        ;;
esac

service_directory="$repository_root/services/$service_name"
shared_environment="$repository_root/.env.shared.local"
maven_wrapper="$service_directory/mvnw"

if [ ! -f "$shared_environment" ]; then
    echo "Local configuration file is missing: $shared_environment" >&2
    echo "Copy .env.shared.local.example to .env.shared.local and provide local values." >&2
    exit 1
fi

if ! sh -n "$shared_environment"; then
    echo "Local configuration is not valid shell assignment syntax: $shared_environment" >&2
    exit 1
fi

# .env.shared.local is a shell environment file, not a Spring property source.
# Export every assignment so the selected Maven/Spring process inherits it.
set -a
# shellcheck disable=SC1090 -- the path is resolved from this script.
. "$shared_environment"
set +a

missing_variables=""
for variable_name in $required_variables; do
    eval "variable_value=\${$variable_name-}"
    if [ -z "$variable_value" ]; then
        missing_variables="$missing_variables $variable_name"
    fi
done

if [ -n "$missing_variables" ]; then
    echo "Missing required configuration for $service_name:" >&2
    for variable_name in $missing_variables; do
        echo "  - $variable_name" >&2
    done
    echo "No configuration values were printed." >&2
    exit 1
fi

if ! command -v lsof >/dev/null 2>&1; then
    echo "Cannot check port $service_port because lsof is not installed or is not on PATH." >&2
    exit 1
fi

listener_pids=$(lsof -nP -t -iTCP:"$service_port" -sTCP:LISTEN 2>/dev/null || true)
if [ -n "$listener_pids" ]; then
    first_pid=$(printf '%s\n' "$listener_pids" | sed -n '1p')
    echo "$service_name cannot start: port $service_port is already in use by PID $first_pid." >&2
    echo "Stop that process yourself or select the service already running there; this launcher never kills processes." >&2
    exit 1
fi

if [ "${1-}" = "--check" ]; then
    echo "Configuration and port $service_port are ready for $service_name."
    exit 0
fi

if [ ! -x "$maven_wrapper" ]; then
    echo "Maven wrapper is missing or not executable: $maven_wrapper" >&2
    exit 1
fi

cd "$service_directory"
exec ./mvnw spring-boot:run "$@"
