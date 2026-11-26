# EFA hardware CI

GitHub starts and monitors the `GlideRdmaEfa` CodeBuild project through the
`AWS_ACTIONS` environment using `AWS_RDMA_ROLE_ARN`. CodeBuild runs
`bin/orchestrate.py`; the ARM client builds both Python clients at
`CODEBUILD_RESOLVED_SOURCE_VERSION` and runs the RDMA transfer tests.
GitHub reports the build status and log link. An unsuccessful build also reports
its failed phase, the failed orchestration step and the pytest result counts. The
log itself stays in CodeBuild.
GitHub serializes dispatches across branches without cancelling the active run;
only the latest pending run is retained. Before cancelling active GitHub monitoring,
cancel any pending runs first: otherwise a pending run can immediately dispatch
another build when the slot is released, while the original CodeBuild build continues.
Wait for that build to finish before dispatching again.

- `.github/buildspec/rdma-efa.yml` documents the CodeBuild runtime and inputs.
- `bin/rdma-pair.sh` manages the host stack using `RDMA_TEMPLATE_URL` and
  `RDMA_INSTANCE_PROFILE_ARN`; it creates the stack without IAM capabilities.
- `bin/run-on.sh` sends the `host/` steps to the pair over SSM.
- `tests/` exercises orchestration and GitHub dispatch without AWS access.
  `.github/workflows/efa-orchestration.yml` runs these checks for tooling changes.

CodeBuild owns teardown. `KEEP_HOSTS=true` retains hosts only on failure.
Cancelling GitHub monitoring leaves CodeBuild running. A hard CodeBuild stop can
skip teardown and leave the stack behind.

Run local tests with PyYAML installed:

```bash
python3 -m unittest discover -s .github/efa/tests -v
```
