# Repository Guidelines

## Project Structure & Module Organization
- `glide-core/` contains the Rust driver and shared protobuf schema; update this first when adding commands.
- Client bindings reside in `python/`, `node/`, `java/`, and `go/`, each with language-specific build tooling and tests.
- `ffi/` bridges Rust to other languages, `utils/` provides shared helpers (notably `cluster_manager.py` for ephemeral clusters), and `examples/` plus `benchmarks/` illustrate usage.
- Use `docs/` for published documentation.

## Build, Test, and Development Commands
- Preferred entry point is `make`: `make <lang>` builds, `make <lang>-test` runs tests, and `make all` executes every language pipeline plus linting.
- Python: `python/dev.py build --mode release`, `python/dev.py test`, and `python/dev.py lint --check` (runs black, isort, flake8, mypy) inside `python/`.
- Node: `npm run build` or `npm run build:release`, `npm test`, and `npm run lint` after `npm i` in `node/`.
- Java: `./gradlew :client:buildAllRelease` and `./gradlew :integTest:test` from `java/`; ensure `valkey-server` or `redis-server` is reachable (`make check-valkey-server`).
- Go: `make -C go build`, `make -C go test`, and `make -C go lint`; `make -C go generate-protobuf` refreshes stubs after protocol changes.

## Coding Style & Naming Conventions
- Python modules follow Black formatting, 4-space indentation, snake_case APIs mirroring command names, and static typing enforced via mypy.
- TypeScript sources use ESLint + Prettier, camelCase exports, and colocated `*.test.ts` files.
- Go code must pass gofumpt/golines and keep the SPDX header noted in `go/Makefile`; run `goimports` only via provided recipes.
- Rust code should stay rustfmt-clean; keep module names aligned with command groups and regenerate bindings when protobufs change.

## Testing Guidelines
- `python/tests/` holds async and sync suites; name files `test_*.py` and reuse pytest markers for backend-specific skips.
- Node tests run with Jest; prefer tight `describe` scopes per feature and store fixtures under `node/tests/ServerModules`.
- Java integration tests live in `java/integTest`; gate slow scenarios with Gradle categories so CI can filter them.
- Go suites rely on `go test` with testify; the example and integration targets expect clusters created via `utils/cluster_manager.py`.

## Commit & Pull Request Guidelines
- Keep commit subjects imperative; Conventional Commit prefixes (`fix:`, `feat:`) are welcome but not mandatory—stay consistent within a series.
- PRs should target `main`, outline which languages were touched, document manual setup (TLS modules, servers), and attach test or lint command output.
- Link related issues before sizable feature work and monitor CI until green; update CHANGELOG entries only when release managers request it.
