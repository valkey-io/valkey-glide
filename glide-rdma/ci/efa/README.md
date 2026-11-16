# RDMA / EFA hardware test for valkey-glide

Runs valkey-glide's RDMA tests against a real EFA fabric: two Graviton EC2 hosts
with Elastic Fabric Adapters in a cluster placement group. `rdma-server` runs
valkey-server with the
[valkey-large-object](https://github.com/valkey-io/valkey-large-object) module;
`rdma-client` builds (or installs) the `valkey-glide-sync` wheel and runs the
RDMA test suite against it over `efa-direct`.

The hosts are a CloudFormation stack. Every host step is a script under
`host/`, sent through SSM. Nothing is reachable from outside the hosts' own
security group; there is no SSH.

There are two ways to run this: from CI on a manual dispatch (the common path,
below), or by hand from your own machine against any AWS account (the rest of
this file). Everything under this directory works both ways — CI just calls the
same `bin/` scripts.

## Running it in CI (GitHub Actions)

The `test-efa` job in `.github/workflows/glide-rdma-efa.yml` runs this against the
PR's own code on a `workflow_dispatch`. A sibling `build-rdma-wheels` job builds
both Python clients with RDMA and hands the wheels to the test host; the EFA
pair is created, exercised, and torn down in one job behind the `AWS_ACTIONS`
environment (a maintainer approval per run).

One-time setup in the AWS account, by an administrator:

```bash
aws cloudformation deploy \
  --stack-name rdma-ci-github-role \
  --template-file glide-rdma/ci/efa/infra/github-role.yaml \
  --capabilities CAPABILITY_NAMED_IAM
```

That creates the OIDC role GitHub assumes and the S3 bucket the wheels pass
through. Then set, in the repository:

| Kind | Name | Value |
|---|---|---|
| Secret | `AWS_RDMA_ROLE_ARN` | stack output `RoleArn` |
| Variable | `RDMA_WHEEL_BUCKET` | stack output `WheelBucketName` |
| Variable | `RDMA_VPC_ID` | the VPC to launch in |
| Variable | `RDMA_SUBNETS` | candidate subnets, comma-separated (tried in order on capacity failure) |

Dispatch it from the Actions tab or `gh workflow run glide-rdma-efa.yml`
(`-f keep_hosts=true` leaves a failed pair up for diagnosis). A scheduled
`glide-rdma-sweep.yml` deletes any `rdma-ci-<run_id>` stack a hard-cancelled run
left behind; each host also self-terminates after `MaxHostLifetimeMinutes` so a
stranded pair cannot bill indefinitely.

## Running it by hand, on your own infra

Everything below drives the same scripts directly, against any account and
region — you need two EFA-capable hosts and the AWS CLI, nothing valkey-glide
specific. The `host/` scripts need only root plus `SERVER_IP`/`CLIENT_IP`, so
two machines with libfabric >= 2.4 and a passing `fi_pingpong` can run them over
SSH instead of SSM if you prefer.

## Prerequisites

On your machine:

- [AWS CLI v2](https://docs.aws.amazon.com/cli/latest/userguide/getting-started-install.html).
- `python3` (used by `bin/run-on.sh` to build the SSM command parameters).
- Credentials for your AWS account in the default profile, with a role that can
  create the stack and run SSM commands (the CI uses a scoped OIDC role; by hand,
  an `Admin`-like role is simplest). A create-only role without SSM permissions
  can bring the hosts up but cannot run the host steps. Check with:

  ```bash
  aws sts get-caller-identity --query Arn --output text
  ```

In the account, already present:

- A VPC with a subnet whose instances can reach the internet (a public IP, or
  a route to a NAT gateway). The SSM agent needs outbound access; the hosts'
  security group admits nothing from outside.
- Enough c8gn.16xlarge capacity in the chosen zone (see Troubleshooting).

## Setup: create the hosts

The VPC and candidate subnets come from the environment, so set them once:

```bash
export RDMA_VPC_ID=vpc-...
export RDMA_SUBNETS=subnet-...,subnet-...   # tried in order on InsufficientInstanceCapacity
./bin/rdma-pair.sh apply
```

Without `RDMA_SUBNETS` or `SubnetId=`, `apply` lists the VPC's public subnets
by zone and stops (`RDMA_VPC_ID` must be set for that). `AWS_REGION` (default
`us-east-1`), `RDMA_STACK` (default `rdma-pair`) and `RDMA_OWNER` (default
your user name, used for the `Owner` tag) are also read from the environment,
so two pairs can coexist under different stack names.

This builds a change set, prints the resources it will create, executes it,
and streams the stack events until the stack settles. It takes about six
minutes: two for the IAM instance profile to propagate, then the two hosts
boot and run the EFA bootstrap. The stack reaches `CREATE_COMPLETE` only after
both hosts have signalled that the EFA device is up and libfabric is 2.4 or
newer, so a green stack is a usable pair. A create that fails on capacity is
deleted and retried in the next subnet of `RDMA_SUBNETS`; any other failure
leaves the stack in place for inspection.

What gets created (`infra/rdma-pair.yaml`):

| Resource | Notes |
|---|---|
| Cluster placement group | Both hosts in one zone, close together |
| Security group | Inbound and outbound to itself only (EFA needs this); all outbound |
| Instance role and profile | `AmazonSSMManagedInstanceCore` plus `cloudformation:SignalResource` on this stack, under IAM path `/rdma-ci/` |
| Launch template | AL2023 arm64 (latest via SSM parameter), one EFA interface, 300 GB gp3 root, IMDSv2 |
| `rdma-server`, `rdma-client` | c8gn.16xlarge each, 64 vCPU, 128 GiB, 200 Gbit networking |

Parameters can be overridden on the command line, for example a single zone
(no retry), the 48xl variant with two EFAs, or a private subnet behind a NAT
gateway:

```bash
./bin/rdma-pair.sh apply SubnetId=subnet-...
./bin/rdma-pair.sh apply InstanceType=c8gn.48xlarge
./bin/rdma-pair.sh apply SubnetId=subnet-... AssignPublicIp=false
```

`apply` on an existing pair is an update. Parameters not given keep their
current values, and `plan` prints a `Replacement` column: a change to the
subnet, instance type, image, EFA installer version or the instance role
replaces both hosts and reruns the 20-minute bootstrap. For a template change
of that kind, `down` and a fresh `apply` is the same cost with a clean slate.

Other lifecycle commands:

| Command | Effect |
|---|---|
| `./bin/rdma-pair.sh status` | Stack state and both instances' state and private IPs |
| `./bin/rdma-pair.sh stop` | Stop both hosts. Instance billing stops; the 300 GB volumes keep the builds |
| `./bin/rdma-pair.sh start` | Start them again. The valkey server does not restart on its own; re-run step 6 |
| `./bin/rdma-pair.sh down` | Delete the stack and everything in it |
| `./bin/rdma-pair.sh plan` | Build and print a change set without executing it |

## Build and run: the host steps

Each step is one command. `bin/run-on.sh` ships the named script to the host
through SSM `send-command`, waits for it, and prints its output. The script
runs as root on the host with `SERVER_IP` and `CLIENT_IP` set; extra
`KEY=value` arguments after the script name become environment variables for
it. Variables set in your own shell do not reach the host, so
`GLIDE_VERSION=2.6.0 ./bin/run-on.sh ...` has no effect; put the override
after the script name instead.

```bash
./bin/run-on.sh <server|client> host/<script>.sh [KEY=value ...]
```

Run in this order:

| # | Command | What it does |
|---|---|---|
| 1 | `./bin/run-on.sh server host/00-check-efa.sh` | Confirms EFA, libfabric version and memlock from an SSM-started process |
| 1 | `./bin/run-on.sh client host/00-check-efa.sh` | Same, on the client |
| 2 | `./bin/run-on.sh server host/10-pingpong-listen.sh` | Starts an `fi_pingpong` listener (returns at once; listens for 180 s) |
| 3 | `./bin/run-on.sh client host/11-pingpong-connect.sh` | Drives `fi_pingpong` against it. A pass proves the EFA path with none of our software involved |
| 4 | `./bin/run-on.sh server host/12-pingpong-result.sh` | The listener's side of step 3 |
| 5 | `./bin/run-on.sh server host/20-server-build.sh` | Installs the toolchain and Rust; builds valkey-server (`unstable`) and the module. About 1 minute on 64 cores |
| 6 | `./bin/run-on.sh server host/21-server-start.sh` | Starts valkey-server with the module (`fabric-provider EfaDirect`) as a systemd unit on the private IP, port 7380 |
| 7 | `./bin/run-on.sh client host/30-client-setup.sh` | Installs the `valkey-glide-sync` and `valkey-glide` wheels in a venv, clones valkey-glide for its tests, checks `rdma_usable()` and a `BLOB.TCP_SET`/`TCP_GET` round trip over TCP |
| 8 | `./bin/run-on.sh client host/31-client-rdma-tests.sh` | Runs `TestRdmaTransfers` from `tests/sync_tests/test_sync_rdma.py` over `efa-direct` |
| 9 | `./bin/run-on.sh client host/32-client-large-object.sh` | `RUNS` distinct files of `SIZE_MB` random bytes, each sent over RDMA and read back into its own window of registered memory, verified in memory per run and by SHA-256 of the file pair. Processed `BATCH` at a time, so disk and pinned memory are bounded by `BATCH`. Prints set/get timing and throughput |

Steps 5 and 7 are idempotent: an existing clone, binary or venv is kept.
Step 6 replaces a running server.

Step 8 is correctness coverage at 4 KiB per transfer; step 9 is the size and
throughput check. Runs go `BATCH` at a time. Per batch: write the files,
register one region of `2 x BATCH x SIZE_MB` and give each run a source
window and a destination window in it, read each file into its source window,
then per run set, get, and compare the two windows in memory; afterwards
write the read-backs, compare the SHA-256 of each file pair, delete the files
and close the region. The timed columns hold only the RDMA legs. A run with
`SIZE_MB=512 RUNS=25 BATCH=10` looks like:

```
-- batch 1-10 of 25
wrote 10 files of 512 MiB in 11.642 s
registered one region of 10240 MiB in 0.158 s
read 10 files into their windows in 0.261 s
 run   set (s)  set MiB/s   get (s)  get MiB/s      crc32c  verify (s)
   1     0.045    11289.6     0.037    14010.7  0x48e1918c       0.028
   2     0.044    11748.6     0.036    14138.0  0xe976e4c5       0.028
   ...
wrote 10 read-back files in 0.416 s
run    1: sha256 964140a0b5993d1d...  match
   ...
digests compared and files removed in 8.551 s
-- batch 11-20 of 25
   ...
-- all 25 runs
set: median 0.044 s (11762.6 MiB/s), min 0.043 s, max 0.045 s over 25 runs
get: median 0.036 s (14053.5 MiB/s), min 0.036 s, max 0.037 s over 25 runs
all 25 objects of 512 MiB round-tripped intact
```

A batch pins `2 x BATCH x SIZE_MB` of memory and writes the same amount to
disk; the driver refuses to start if either exceeds 80 % of what is free.
`RUNS` itself is unbounded. Each batch of ten 512 MiB runs takes about 25 s,
most of it generating random bytes and hashing files, outside the timed legs.

Overrides, as `KEY=value` after the script name:

| Script | Variable | Default |
|---|---|---|
| `20-server-build.sh` | `VALKEY_REF` | `unstable` |
| | `VLO_REF` | `main` |
| `21-server-start.sh`, `30-*`, `31-*` | `PORT` | `7380` (pass the same value to all three) |
| `21-server-start.sh` | `DRAM_MAXMEMORY`, `RDMA_REGISTERED` | 16 GiB, 32 GiB, as byte counts |
| `30-client-setup.sh` | `GLIDE_WHEELS` | unset — a presigned S3 URL for a tar of prebuilt wheels (CI's path); needs `GLIDE_SOURCE` for the test-suite checkout |
| | `GLIDE_SOURCE` | unset — a full commit SHA, tag, or branch; builds both clients from that checkout (what a change-under-test needs) |
| | `GLIDE_VERSION` | `2.6.0rc2` (the PyPI wheel), used when neither of the above is set |
| | `GLIDE_REF` | the git tag matching the wheel, `v2.6.0-rc2` for `2.6.0rc2` |
| `31-client-rdma-tests.sh` | `TEST_FILTER` | `TestRdmaTransfers` (a pytest `-k` expression) |
| | `GLIDE_RDMA_PROVIDER` | `efa-direct` |
| `32-client-large-object.sh` | `SIZE_MB` | `256`. The module's `max-object-size` default is 512 MiB |
| | `RUNS` | `3` |
| | `BATCH` | `10` runs per batch; bounds disk and pinned memory |

For example, to test a different wheel against the matching test suite:

```bash
./bin/run-on.sh client host/30-client-setup.sh GLIDE_VERSION=2.6.0rc4
./bin/run-on.sh client host/31-client-rdma-tests.sh
```

The wheel does not record the commit it was built from, so the checkout is
the tag with the same version. A final wheel can appear on PyPI before the
repo has its tag (2.6.0 was published while git had only `v2.6.0-rc4`); the
script then stops and lists the nearest tags, and `GLIDE_REF=` picks one:

```bash
./bin/run-on.sh client host/30-client-setup.sh GLIDE_VERSION=2.6.0 GLIDE_REF=v2.6.0-rc4
```

Each script's output is also kept on the host under `/var/log/rdma/`, which
matters for step 5: SSM caps returned output at 24 000 characters, and the
scripts tail their build logs on failure.

## Expected result

Step 8 ends with:

```
======================= 8 passed, 95 deselected in 5.39s =======================
```

The 95 deselected tests are the rest of `test_sync_rdma.py`, which use the
harness's pooled client fixtures and a conventional server. `TEST_FILTER=`
widens the selection.

## Troubleshooting

- **`apply` fails with `InsufficientInstanceCapacity`.** c8gn is a new family
  and per-zone capacity varies. The error names zones that have it. With
  `RDMA_SUBNETS` set, `apply` already moved through every listed subnet;
  add one in a zone the error names, or pass it directly with
  `apply SubnetId=...`. `apply` with no subnet configured prints the VPC's
  public subnets by zone.
- **`apply` fails on the instance signal.** The stack keeps a failed create
  (`--on-stack-failure DO_NOTHING`) so the hosts can be inspected. Read
  `/var/log/rdma-bootstrap.log` on the host through `run-on.sh`, or the EC2
  console output (`aws ec2 get-console-output`). Run `down` before the next
  `apply`.
- **Step 3 fails.** The AWS side: security group self-rules, placement group,
  or the EFA install. Not GLIDE, not the module.
- **Step 6 fails.** `journalctl -u rdma-valkey` on the server has the module's
  log lines.
- **Step 7 fails on `rdma_usable`.** The wheel could not load libfabric or
  found one older than 2.4; `fi_info --version` says what is installed.
- **Step 8 fails with step 7 green.** The RDMA path itself, which is what these
  hosts exist to exercise.

`docs/RUNBOOK.md` has the design decisions behind this layout, the notes from
the first full pass, and what each failure mode looked like.
