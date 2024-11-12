.PHONY: all java java-test python python-test node node-test check-redis-server go go-test

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

java-test: check-redis-server
	@echo "$(GREEN)Running integration tests$(RESET)"
	@cd java && ./gradlew :integTest:test

##
## Python targets
##
python: .build/python_deps
	@echo "$(GREEN)Building for Python (release)$(RESET)"
	@cd python && VIRTUAL_ENV=$(PYENV_DIR) .env/bin/maturin develop --release --strip

python-lint: .build/python_deps
	@echo "$(GREEN)Building Linters for python$(RESET)"
	cd python && 																		\
		export VIRTUAL_ENV=$(PYENV_DIR); 												\
		export PYTHONPATH=$(PY_PATH):$(PY_GLIDE_PATH); 									\
		export PATH=$(PYENV_DIR)/bin:$(PATH);		 									\
		isort . --profile black --skip-glob python/glide/protobuf --skip-glob .env && 	\
		black . --exclude python/glide/protobuf --exclude .env && 						\
		flake8 . --count --select=E9,F63,F7,F82 --show-source --statistics      		\
			--exclude=python/glide/protobuf,.env/* --extend-ignore=E230				&&	\
		flake8 . --count --exit-zero --max-complexity=12 --max-line-length=127  		\
			--statistics --exclude=python/glide/protobuf,.env/*             			\
			--extend-ignore=E230

python-test: .build/python_deps check-redis-server
	cd python && PYTHONPATH=$(PY_PATH):$(PY_GLIDE_PATH) .env/bin/pytest --asyncio-mode=auto

.build/python_deps:
	@echo "$(GREEN)Generating protobuf files...$(RESET)"
	@protoc -Iprotobuf=$(ROOT_DIR)/glide-core/src/protobuf/ \
		--python_out=$(ROOT_DIR)/python/python/glide $(ROOT_DIR)/glide-core/src/protobuf/*.proto
	@echo "$(GREEN)Building environment...$(RESET)"
	@cd python && python3 -m venv .env
	@echo "$(GREEN)Installing requirements...$(RESET)"
	@cd python && .env/bin/pip install -r requirements.txt
	@cd python && .env/bin/pip install -r dev_requirements.txt
	@mkdir -p .build/ && touch .build/python_deps

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

node-test: .build/node_deps check-redis-server
	@echo "$(GREEN)Running tests for NodeJS$(RESET)"
	@cd node && npm run build
	cd node && npm test

node-lint: .build/node_deps
	@echo "$(GREEN)Running linters for NodeJS$(RESET)"
	@cd node && npx run lint:fix

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
check-redis-server:
	which redis-server

clean:
	rm -fr .build/

help:
	@echo "$(GREEN)Listing Makefile targets:$(RESET)"
	@echo $(shell grep '^[^#[:space:]].*:' Makefile|cut -d":" -f1|grep -v PHONY|grep -v "^.build"|sort)
