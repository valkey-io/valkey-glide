#!/bin/bash

# Jedis Compatibility Test Runner
# This script runs comprehensive compatibility tests comparing GLIDE Jedis wrapper with actual Jedis

set -e

# Configuration
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
REDIS_HOST="${REDIS_HOST:-localhost}"
REDIS_PORT="${REDIS_PORT:-6379}"
JEDIS_VERSION="${JEDIS_VERSION:-5.1.0}"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Logging functions
log_info() {
    echo -e "${BLUE}[INFO]${NC} $1"
}

log_success() {
    echo -e "${GREEN}[SUCCESS]${NC} $1"
}

log_warning() {
    echo -e "${YELLOW}[WARNING]${NC} $1"
}

log_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

# Function to check if Redis is running
check_redis() {
    log_info "Checking Redis connectivity..."
    if command -v redis-cli >/dev/null 2>&1; then
        if redis-cli -h "$REDIS_HOST" -p "$REDIS_PORT" ping >/dev/null 2>&1; then
            log_success "Redis is running at $REDIS_HOST:$REDIS_PORT"
            return 0
        else
            log_error "Redis is not responding at $REDIS_HOST:$REDIS_PORT"
            return 1
        fi
    else
        log_warning "redis-cli not found, skipping Redis connectivity check"
        return 0
    fi
}

# Function to download actual Jedis for comparison
download_jedis() {
    log_info "Downloading actual Jedis $JEDIS_VERSION for comparison..."
    
    local lib_dir="$PROJECT_ROOT/integTest/build/comparison-libs"
    mkdir -p "$lib_dir"
    
    local jedis_jar="$lib_dir/jedis-comparison.jar"
    
    if [[ -f "$jedis_jar" ]]; then
        log_info "Jedis comparison JAR already exists: $jedis_jar"
        return 0
    fi
    
    # Download using Maven coordinates
    local maven_url="https://repo1.maven.org/maven2/redis/clients/jedis/$JEDIS_VERSION/jedis-$JEDIS_VERSION.jar"
    
    if command -v curl >/dev/null 2>&1; then
        curl -L -o "$jedis_jar" "$maven_url"
    elif command -v wget >/dev/null 2>&1; then
        wget -O "$jedis_jar" "$maven_url"
    else
        log_error "Neither curl nor wget found. Cannot download Jedis JAR."
        return 1
    fi
    
    if [[ -f "$jedis_jar" ]]; then
        log_success "Downloaded Jedis JAR: $jedis_jar"
        return 0
    else
        log_error "Failed to download Jedis JAR"
        return 1
    fi
}

# Function to build the project
build_project() {
    log_info "Building GLIDE project..."
    cd "$PROJECT_ROOT"
    
    if [[ -f "gradlew" ]]; then
        ./gradlew :client:publishToMavenLocal
        log_success "Project built successfully"
    else
        log_error "Gradle wrapper not found"
        return 1
    fi
}

# Function to run compatibility tests
run_compatibility_tests() {
    log_info "Running Jedis compatibility tests..."
    cd "$PROJECT_ROOT/integTest"
    
    local test_args=(
        "compatibilityTest"
        "-Djedis.jar.path=$PROJECT_ROOT/integTest/build/comparison-libs/jedis-comparison.jar"
        "-Dtest.server.standalone=$REDIS_HOST:$REDIS_PORT"
        "-Dcompatibility.test.iterations=${ITERATIONS:-1000}"
        "-Dcompatibility.test.concurrent.clients=${CONCURRENT_CLIENTS:-10}"
        "--info"
    )
    
    if [[ -f "../gradlew" ]]; then
        ../gradlew "${test_args[@]}"
    else
        gradle "${test_args[@]}"
    fi
}

# Function to run performance benchmarks
run_performance_tests() {
    log_info "Running performance benchmarks..."
    cd "$PROJECT_ROOT/integTest"
    
    local benchmark_args=(
        "performanceBenchmark"
        "-Dbenchmark.iterations=${BENCHMARK_ITERATIONS:-10000}"
        "-Dbenchmark.warmup=${BENCHMARK_WARMUP:-1000}"
        "-Dbenchmark.threads=${BENCHMARK_THREADS:-4}"
        "--info"
    )
    
    if [[ -f "../gradlew" ]]; then
        ../gradlew "${benchmark_args[@]}"
    else
        gradle "${benchmark_args[@]}"
    fi
}

# Function to run stress tests
run_stress_tests() {
    log_info "Running stress tests..."
    cd "$PROJECT_ROOT/integTest"
    
    local stress_args=(
        "stressTest"
        "-Dstress.duration=${STRESS_DURATION:-300}"
        "-Dstress.clients=${STRESS_CLIENTS:-50}"
        "-Dstress.operations=${STRESS_OPERATIONS:-1000}"
        "--info"
    )
    
    if [[ -f "../gradlew" ]]; then
        ../gradlew "${stress_args[@]}"
    else
        gradle "${stress_args[@]}"
    fi
}

# Function to run the dual test runner
run_dual_test_runner() {
    log_info "Running dual Jedis test runner..."
    cd "$PROJECT_ROOT/integTest"
    
    # Compile and run the DualJedisTestRunner
    local classpath="build/classes/java/test:build/resources/test"
    
    # Add GLIDE client to classpath
    local glide_jar=$(find "$HOME/.m2/repository/io/valkey/valkey-glide" -name "*.jar" | head -1)
    if [[ -n "$glide_jar" ]]; then
        classpath="$classpath:$glide_jar"
    fi
    
    # Add comparison Jedis to classpath
    local jedis_jar="$PROJECT_ROOT/integTest/build/comparison-libs/jedis-comparison.jar"
    if [[ -f "$jedis_jar" ]]; then
        classpath="$classpath:$jedis_jar"
    fi
    
    java -cp "$classpath" \
         -Djedis.jar.path="$jedis_jar" \
         glide.DualJedisTestRunner
}

# Function to generate test report
generate_report() {
    log_info "Generating compatibility test report..."
    cd "$PROJECT_ROOT/integTest"
    
    if [[ -f "../gradlew" ]]; then
        ../gradlew compatibilityReport
    else
        gradle compatibilityReport
    fi
    
    local report_file="build/reports/compatibility-report.md"
    if [[ -f "$report_file" ]]; then
        log_success "Compatibility report generated: $report_file"
        echo
        echo "=== Report Summary ==="
        head -20 "$report_file"
    fi
}

# Function to display usage
usage() {
    cat << EOF
Usage: $0 [OPTIONS] [COMMAND]

Commands:
    all             Run all compatibility tests (default)
    compatibility   Run basic compatibility tests only
    performance     Run performance benchmarks only
    stress          Run stress tests only
    dual            Run dual test runner only
    report          Generate compatibility report only

Options:
    -h, --help                  Show this help message
    -r, --redis-host HOST       Redis host (default: localhost)
    -p, --redis-port PORT       Redis port (default: 6379)
    -j, --jedis-version VER     Jedis version to compare against (default: 5.1.0)
    -i, --iterations N          Number of test iterations (default: 1000)
    -c, --concurrent-clients N  Number of concurrent clients (default: 10)
    -b, --benchmark-iterations N Number of benchmark iterations (default: 10000)
    -t, --benchmark-threads N   Number of benchmark threads (default: 4)
    -s, --stress-duration N     Stress test duration in seconds (default: 300)
    --skip-redis-check          Skip Redis connectivity check
    --skip-download             Skip Jedis JAR download
    --skip-build                Skip project build

Environment Variables:
    REDIS_HOST                  Redis host
    REDIS_PORT                  Redis port
    JEDIS_VERSION              Jedis version
    ITERATIONS                 Test iterations
    CONCURRENT_CLIENTS         Concurrent clients
    BENCHMARK_ITERATIONS       Benchmark iterations
    BENCHMARK_THREADS          Benchmark threads
    STRESS_DURATION           Stress test duration
    STRESS_CLIENTS            Stress test clients
    STRESS_OPERATIONS         Stress test operations per client

Examples:
    $0                          # Run all tests with defaults
    $0 compatibility            # Run only compatibility tests
    $0 performance -b 50000     # Run performance tests with 50k iterations
    $0 -r redis.example.com -p 6380 all  # Use custom Redis server

EOF
}

# Parse command line arguments
COMMAND="all"
SKIP_REDIS_CHECK=false
SKIP_DOWNLOAD=false
SKIP_BUILD=false

while [[ $# -gt 0 ]]; do
    case $1 in
        -h|--help)
            usage
            exit 0
            ;;
        -r|--redis-host)
            REDIS_HOST="$2"
            shift 2
            ;;
        -p|--redis-port)
            REDIS_PORT="$2"
            shift 2
            ;;
        -j|--jedis-version)
            JEDIS_VERSION="$2"
            shift 2
            ;;
        -i|--iterations)
            ITERATIONS="$2"
            shift 2
            ;;
        -c|--concurrent-clients)
            CONCURRENT_CLIENTS="$2"
            shift 2
            ;;
        -b|--benchmark-iterations)
            BENCHMARK_ITERATIONS="$2"
            shift 2
            ;;
        -t|--benchmark-threads)
            BENCHMARK_THREADS="$2"
            shift 2
            ;;
        -s|--stress-duration)
            STRESS_DURATION="$2"
            shift 2
            ;;
        --skip-redis-check)
            SKIP_REDIS_CHECK=true
            shift
            ;;
        --skip-download)
            SKIP_DOWNLOAD=true
            shift
            ;;
        --skip-build)
            SKIP_BUILD=true
            shift
            ;;
        all|compatibility|performance|stress|dual|report)
            COMMAND="$1"
            shift
            ;;
        *)
            log_error "Unknown option: $1"
            usage
            exit 1
            ;;
    esac
done

# Main execution
main() {
    echo "=== Jedis Compatibility Test Runner ==="
    echo "Command: $COMMAND"
    echo "Redis: $REDIS_HOST:$REDIS_PORT"
    echo "Jedis Version: $JEDIS_VERSION"
    echo

    # Pre-flight checks
    if [[ "$SKIP_REDIS_CHECK" != "true" ]]; then
        if ! check_redis; then
            log_error "Redis connectivity check failed. Use --skip-redis-check to bypass."
            exit 1
        fi
    fi

    if [[ "$SKIP_BUILD" != "true" ]]; then
        if ! build_project; then
            log_error "Project build failed"
            exit 1
        fi
    fi

    if [[ "$SKIP_DOWNLOAD" != "true" ]]; then
        if ! download_jedis; then
            log_warning "Failed to download Jedis JAR. Comparison tests will be skipped."
        fi
    fi

    # Execute requested command
    case "$COMMAND" in
        all)
            run_compatibility_tests
            run_performance_tests
            run_stress_tests
            run_dual_test_runner
            generate_report
            ;;
        compatibility)
            run_compatibility_tests
            ;;
        performance)
            run_performance_tests
            ;;
        stress)
            run_stress_tests
            ;;
        dual)
            run_dual_test_runner
            ;;
        report)
            generate_report
            ;;
        *)
            log_error "Unknown command: $COMMAND"
            exit 1
            ;;
    esac

    log_success "Compatibility testing completed!"
    echo
    echo "Results can be found in:"
    echo "  - Test reports: $PROJECT_ROOT/integTest/build/reports/tests/"
    echo "  - Compatibility report: $PROJECT_ROOT/integTest/build/reports/compatibility-report.md"
}

# Run main function
main "$@"
