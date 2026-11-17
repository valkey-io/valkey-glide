# RDMA test pair: manual runbook

Two c8gn.16xlarge Graviton hosts with EFA in one cluster placement group, in
ValkeyClientTesting (507286591552), us-east-1. `rdma-server` runs valkey-server
with the [valkey-large-object](https://github.com/valkey-io/valkey-large-object)
module; `rdma-client` runs the published `valkey-glide-sync` wheel and
valkey-glide's RDMA tests against it. Nothing is reachable from outside the
security group; every host step goes through SSM.

Credentials: the `Admin` role, vended per session. `DeveloperAccess` can create
the EC2 side but has no SSM permissions.

## Layout

| Path | Role |
|---|---|
| `infra/rdma-pair.yaml` | CloudFormation stack: placement group, security group, SSM-only instance role, launch template, two instances. UserData installs EFA and signals the stack, so `CREATE_COMPLETE` means both hosts have a working EFA device and libfabric >= 2.4. |
| `bin/rdma-pair.sh` | `apply` / `status` / `stop` / `start` / `down`, with stack events streamed. |
| `bin/run-on.sh` | Runs one `host/` script on `server` or `client` through SSM `send-command` and prints its output. Sets `SERVER_IP` and `CLIENT_IP` for the script. |
| `host/*.sh` | The host steps, numbered in run order. Each is idempotent where that is cheap. |

## Steps

Phase 0 is the stack. Everything after it is `bin/run-on.sh <role> host/<script>`.

| # | Host | Script | Proves |
|---|---|---|---|
| 0 | | `bin/rdma-pair.sh apply` | Hosts exist, EFA installed, libfabric >= 2.4, memlock unlimited for SSM-started processes |
| 1 | both | `00-check-efa.sh` | The same, seen from an SSM-started process |
| 2 | server | `10-pingpong-listen.sh` | (starts the listener, returns at once) |
| 3 | client | `11-pingpong-connect.sh` | RDMA traffic flows between the hosts over EFA, with none of our software involved |
| 4 | server | `12-pingpong-result.sh` | (listener's side of step 3) |
| 5 | server | `20-server-build.sh` | valkey-server (`unstable`) and the module build on AL2023 arm64 |
| 6 | server | `21-server-start.sh` | The module loads with `fabric-provider EfaDirect` and answers PING on the private IP |
| 7 | client | `30-client-setup.sh` | The wheel reports `rdma_usable() == True`; a `BLOB.TCP_SET`/`TCP_GET` round trip works over TCP |
| 8 | client | `31-client-rdma-tests.sh` | valkey-glide's `TestRdmaTransfers` pass over `efa-direct` |

Stop between sessions with `bin/rdma-pair.sh stop`; the 300 GB volumes keep the
builds. `down` deletes everything.

## Where failures point

- Step 0 fails on the signal: read `/var/log/rdma-bootstrap.log` on the host.
  The stack keeps a failed create (`--on-stack-failure DO_NOTHING`) so the host
  is still there; `bin/rdma-pair.sh down` removes it afterwards. The EC2 console
  output (`aws ec2 get-console-output`) carries the same log without SSM.
- Step 0 fails on capacity: c8gn is a new family and `InsufficientInstanceCapacity`
  per zone is common. `apply` retries through `RDMA_SUBNETS` in order; add a
  subnet in a zone the error names, or pass `apply SubnetId=<subnet>` directly.
- Step 3 fails: the AWS side. Security group self-rules, placement group, or the
  EFA install. Not GLIDE, not the module.
- Step 6 fails: the module did not load or could not register memory. The
  journal (`journalctl -u rdma-valkey`) has the module's log lines.
- Step 7 fails on `rdma_usable`: the wheel could not `dlopen` libfabric or found
  one older than 2.4. `fi_info --version` says which is installed.
- Step 8 fails with step 7 green: the RDMA path itself. This is the interesting
  case, and the one these hosts exist to find.

## Review notes on this path

Decisions taken while mapping it, and what was weighed.

- **EFA install in the stack, everything else outside it.** The EFA stack is a
  fixed host fact and is cheap to verify at boot, so making it part of
  `CREATE_COMPLETE` means a green stack is a usable pair. The server build and
  the client install change often (the module's `main` moved the day this was
  written), and a UserData change replaces both instances, so they stay as
  re-runnable SSM steps.
- **valkey `unstable`, not a release.** The module's CI builds against
  `unstable` on arm64. A release tag may work; `VALKEY_REF=` overrides it.
- **The wheel, not a source build.** `valkey-glide-sync 2.6.0rc2` ships aarch64
  wheels with the RDMA entry points compiled in, so the client host needs no
  Rust. The valkey-glide checkout is only for the test suite. The async wheel is
  installed too, because the test harness imports `glide`.
- **Transfer tests only (`-k TestRdmaTransfers`).** The rest of
  `test_sync_rdma.py` uses the harness's pooled client fixtures, which assume a
  conventional valkey. The transfer class builds its own client from
  `GLIDE_RDMA_SERVER`. `TEST_FILTER=` widens it.
- **Explicit memory knobs on the server.** The module defaults
  `max-rdma-registered-bytes` to 380 GB; the host has 128 GiB and no local
  NVMe, so Dram mode with 16 GB of pool and 32 GB of registration is the
  starting point. Both are environment overrides on `21-server-start.sh`.
- **A transient systemd unit for the server.** A process started directly by
  `send-command` dies with the command. `systemd-run` gives it a unit, a
  journal, and an explicit `LimitMEMLOCK=infinity`.
- **`fi_pingpong` before anything else.** It is the one check that separates
  "the AWS setup is wrong" from "our software is wrong", and it costs a minute.

## First full pass (2026-11-03, us-east-1b)

Every step ran green once the fixes below landed. Times are from the 64-core
c8gn.16xlarge: EFA installer 82 s, valkey-server build about 40 s, module build
about 15 s, the 8 `TestRdmaTransfers` tests 5.4 s.

What the live hosts corrected in the scripts:

- The AMI's `gnupg2-minimal` has neither `gpg-agent` nor `gpgv`, and plain
  `dnf install gnupg2` conflicts with it; `dnf swap` is the working form.
- The EFA installer says "Reboot is needed!" because the AMI's in-tree `efa`
  module is already loaded and cannot be unloaded. The device works over the
  in-tree driver; the DKMS build takes over at the next boot.
- `ofi-libfabric-sys` finds libfabric through pkg-config, and the installer's
  `.pc` file is under `/opt/amazon/efa/lib64/pkgconfig`, not on the default path.
- The module's load-time arguments are plain integers. The `16gb` notation
  the README describes only works through `CONFIG SET`.
- Valkey bound to a non-loopback address with no password refuses non-loopback
  clients unless `--protected-mode no`; the security group is the boundary here.
- `valkey-cli` exits 0 on an error reply, so a PING check has to compare the
  reply text.
- `send-command` reports `Success` for a failed script when its output goes
  through `tee`; the wrapper sets `pipefail`.


## Open questions

- The `send-command` output is capped at 24 000 characters. Long build logs are
  on the host under `/var/log/rdma/`; the scripts tail them on failure.
- The first full pass ran on hosts whose stack was `CREATE_FAILED` (they were
  finished by hand after the gnupg fix), so the committed bootstrap has not yet
  produced a `CREATE_COMPLETE` on its own. The next `down` + `apply` will.
