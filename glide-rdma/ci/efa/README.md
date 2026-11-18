# EFA hardware CI

GitHub starts and monitors the `GlideRdmaEfa` CodeBuild project through the
`AWS_ACTIONS` environment using `AWS_RDMA_ROLE_ARN`. CodeBuild runs
`bin/orchestrate.py`; the ARM client builds both Python clients at
`CODEBUILD_RESOLVED_SOURCE_VERSION` and runs the RDMA transfer tests.

- `.github/buildspec/rdma-efa.yml` documents the CodeBuild runtime and inputs.
- `bin/rdma-pair.sh` manages the host stack using `RDMA_TEMPLATE_URL`.
- `bin/run-on.sh` sends the `host/` steps to the pair over SSM.
- `tests/` exercises orchestration and GitHub dispatch without AWS access.

CodeBuild owns teardown. `KEEP_HOSTS=true` retains hosts only on failure.
Cancelling GitHub monitoring leaves CodeBuild running. Hard CodeBuild stops can
skip teardown; host lifetime limits and manual stack cleanup are managed by the
infrastructure configuration.

CloudFormation templates, AWS setup, and operational runbooks belong in the
CodeBuild infrastructure repository.

Run local tests with PyYAML installed:

```bash
python3 -m unittest discover -s glide-rdma/ci/efa/tests -v
```
