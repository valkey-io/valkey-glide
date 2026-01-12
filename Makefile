.PHONY: all java java-test python python-test node node-test check-valkey-server go go-test prettier-check prettier-fix

BLUE=\033[34m
YELLOW=\033[33m
GREEN=\033[32m
RESET=\033[0m
ROOT_DIR=$(shell pwd)
PYENV_DIR=$(shell pwd)/python/.env
PY_PATH=$(shell find python/.env -name "site-packages"|xargs readlink -f)
PY_GLIDE_PATH=$(shell pwd)/python/python/

all: java java-test python python-test node node-test go go-test python-lint java-lint

##
## Java targets
##
java:
	@echo "$(GREEN)Building for Java (release)$(RESET)"
	@cd java && ./gradlew :client:buildAllRelease

java-lint:
	@echo "$(GREEN)Running spotlessApply$(RESET)"
	@cd java && ./gradlew :spotlessApply

java-test: check-valkey-server
	@echo "$(GREEN)Running integration tests$(RESET)"
	@cd java && ./gradlew :integTest:test

##
## Python targets
##
python:
	@echo "$(GREEN)Building Python async + sync clients (release mode)$(RESET)"
	@cd python && python3 dev.py build --mode release

python-lint:
	@echo "$(GREEN)Running linters via dev.py$(RESET)"
	@cd python && python3 dev.py lint

python-test: check-valkey-server
	@echo "$(GREEN)Running Python tests$(RESET)"
	@cd python && python3 dev.py test

##
## NodeJS targets
##
node: .build/node_deps
	@echo "$(GREEN)Building for NodeJS (release)...$(RESET)"
	@cd node && npm run build:release

.build/node_deps:
	@echo "$(GREEN)Installing NodeJS dependencies...$(RESET)"
	@cd node && npm i
	@cd node/rust-client && npm i
	@mkdir -p .build/ && touch .build/node_deps

node-test: .build/node_deps check-valkey-server
	@echo "$(GREEN)Running tests for NodeJS$(RESET)"
	@cd node && npm run build
	cd node && npm test

node-lint: .build/node_deps
	@echo "$(GREEN)Running linters for NodeJS$(RESET)"
	@cd node && npx run lint:fix

##
## Prettier targets
##
prettier-check:
	@echo "$(GREEN)Checking formatting with Prettier$(RESET)"
	@npx prettier --check .github/
	@for folder in node benchmarks/node benchmarks/utilities; do \
		npx prettier --check $$folder; \
	done

prettier-fix:
	@echo "$(GREEN)Fixing formatting with Prettier$(RESET)"
	@npx prettier --write .github/
	@for folder in node benchmarks/node benchmarks/utilities; do \
		npx prettier --write $$folder; \
	done

##
## Go targets
##


go: .build/go_deps
	$(MAKE) -C go build

go-test: .build/go_deps
	$(MAKE) -C go test

go-lint: .build/go_deps
	$(MAKE) -C go lint

.build/go_deps:
	@echo "$(GREEN)Installing GO dependencies...$(RESET)"
	$(MAKE) -C go install-build-tools install-dev-tools
	@mkdir -p .build/ && touch .build/go_deps

##
## Common targets
##
check-valkey-server:
	which valkey-server || which redis-server

clean:
	rm -fr .build/

help:
	@echo "$(GREEN)Listing Makefile targets:$(RESET)"
	@echo $(shell grep '^[^#[:space:]].*:' Makefile|cut -d":" -f1|grep -v PHONY|grep -v "^.build"|sort)
