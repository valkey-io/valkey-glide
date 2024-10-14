.PHONY: all java java-test python python-test node node-test check-redis-server go go-test

BLUE=\033[34m
YELLOW=\033[33m
GREEN=\033[32m
RESET=\033[0m
ROOT_DIR=$(shell pwd)
PYENV_DIR=$(shell pwd)/python/.env
PY_PATH=$(shell find python/.env -name "site-packages"|xargs readlink -f)
PY_GLIDE_PATH=$(shell pwd)/python/python/

all: java java-test python python-test node node-test go go-test

java:
	@echo "$(GREEN)Building for Java (release)$(RESET)"
	@cd java && ./gradlew :client:buildAllRelease

java-test: check-redis-server
	@echo "$(GREEN)Running spotlessCheck$(RESET)"
	@cd java && ./gradlew :spotlessCheck
	@echo "$(GREEN)Running spotlessApply$(RESET)"
	@cd java && ./gradlew :spotlessApply
	@echo "$(GREEN)Running integration tests$(RESET)"
	@cd java && ./gradlew :integTest:test

python: .build/python_deps
	@echo "$(GREEN)Building for Python (release)$(RESET)"
	@cd python && VIRTUAL_ENV=$(PYENV_DIR) .env/bin/maturin develop --release --strip

# Python dependencies
.build/python_deps:
	@echo "$(GREEN)Generating protobuf files...$(RESET)"
	@protoc -Iprotobuf=$(ROOT_DIR)/glide-core/src/protobuf/ \
		--python_out=$(ROOT_DIR)/python/python/glide $(ROOT_DIR)/glide-core/src/protobuf/*.proto
	@echo "$(GREEN)Building environment...$(RESET)"
	@cd python && python3 -m venv .env
	@echo "$(GREEN)Installing requirements...$(RESET)"
	@cd python && .env/bin/pip install -r requirements.txt
	@mkdir -p .build/ && touch .build/python_deps

python-test: check-redis-server
	cd python && PYTHONPATH=$(PY_PATH):$(PY_GLIDE_PATH) .env/bin/pytest --asyncio-mode=auto

node: .build/node_deps
	@echo "$(GREEN)Building for NodeJS (release)...$(RESET)"
	@cd node && npm run build:release

# NodeJS dependencies
.build/node_deps:
	@echo "$(GREEN)Installing NodeJS dependencies...$(RESET)"
	@cd node && npm i
	@cd node/rust-client && npm i
	@mkdir -p .build/ && touch .build/node_deps

node-test: check-redis-server
	@echo "$(GREEN)Running tests for NodeJS$(RESET)"
	@cd node && npm run build
	cd node && npm test

# Check for the existence of redis-server by simply calling which shell command
check-redis-server:
	which redis-server

go: .build/go_deps
	$(MAKE) -C go build

go-test:
	$(MAKE) -C go test

.build/go_deps:
	@echo "$(GREEN)Installing GO dependencies...$(RESET)"
	$(MAKE) -C go install-build-tools
	@mkdir -p .build/ && touch .build/go_deps

clean:
	rm -fr .build/
