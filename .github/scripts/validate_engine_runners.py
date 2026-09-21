"""Check that every workflow job installing the Valkey engine runs on a runner that
build-matrix.json declares for that job's target.

Two runner images that build the engine for one target share a cache key, and a binary
built on the newer image cannot run on the older one. Every engine job has to be
accounted for here, so a job this script cannot resolve is a failure rather than a skip.
"""

import glob
import json
import os
import re
import sys

import yaml

MATRIX_FILE = ".github/json_matrices/build-matrix.json"
WORKFLOWS = ".github/workflows/*.yml"
# Only these fields pair a runner with the target it was declared for. CD_RUNNER and
# CD_TARGET belong to other entries, so pairing them would reintroduce the mismatch.
RUNNER_FIELDS = ("runner", "self_hosted_runner")
TARGET_FIELDS = ("target",)
# Spelled in pieces so Actions does not read it as an expression to evaluate.
OPEN = "$" + "{{"
REFERENCE = r"matrix\.([A-Za-z0-9_-]+)\.([A-Za-z0-9_-]+)"


def labels(runner):
    """Normalise a runner into comparable labels."""
    names = runner if isinstance(runner, list) else [runner]
    # The CD workflows swap this label, so treat both pools as one image.
    return tuple(sorted(str(name).replace("persistent", "ephemeral") for name in names))


def load_matrix():
    """Runners declared per target, and every field name the matrix file uses."""
    with open(MATRIX_FILE) as handle:
        entries = json.load(handle)
    declared, fields = {}, set()
    for entry in entries:
        fields.update(key.lower() for key in entry)
        target = entry.get("TARGET")
        for key in ("RUNNER", "SELF_HOSTED_RUNNER"):
            if target and entry.get(key):
                declared.setdefault(target, set()).add(labels(entry[key]))
    return declared, fields


def field(mapping, name):
    """Look up a key the way Actions does, without regard to case."""
    for key, value in (mapping or {}).items():
        if key.lower() == name.lower():
            return value
    return None


def references(value):
    """The matrix references in a value, when the value is made only of those."""
    if not isinstance(value, str) or OPEN not in value:
        return []
    body = value.strip()[len(OPEN) : -2]
    parts = [part.strip() for part in body.split("||")]
    if not all(re.fullmatch(REFERENCE, part) for part in parts):
        return []
    return [re.fullmatch(REFERENCE, part).groups() for part in parts]


def literal_runners(value):
    """Every runner an expression can choose, or None when it names none."""
    found = [json.loads(group) for group in re.findall(r"fromJSON\('(\[[^']*\])'\)", value, re.I)]
    found += [group for group in re.findall(r"'([A-Za-z0-9][A-Za-z0-9_-]*)'", value)]
    return found or None


def generating_job(workflow, value):
    """The job that builds a matrix loaded with fromJson(needs.<job>.outputs.<name>)."""
    match = re.search(r"needs\.([A-Za-z0-9_-]+)\.outputs\.[A-Za-z0-9_-]+", value or "")
    if not match:
        return None
    return match.group(1) if match.group(1) in (workflow.get("jobs") or {}) else None


def reads_matrix_file(workflow, job_id):
    """Whether the named job derives its output from build-matrix.json."""
    for step in ((workflow.get("jobs") or {}).get(job_id) or {}).get("steps") or []:
        if MATRIX_FILE in (step.get("run") or ""):
            return True
        uses = step.get("uses") or ""
        if uses.startswith("./"):
            for candidate in (uses[2:], os.path.join(uses[2:], "action.yml")):
                if os.path.isfile(candidate) and MATRIX_FILE in open(candidate).read():
                    return True
    return False


def local_action(uses):
    """The file behind a `uses: ./path` reference, if there is one."""
    if not uses.startswith("./"):
        return None
    for candidate in (uses[2:], os.path.join(uses[2:], "action.yml")):
        if os.path.isfile(candidate):
            return candidate
    return None


def installs_engine(step, depth=2):
    """Whether a step ends up running install-engine, directly or through a wrapper."""
    uses, using = step.get("uses") or "", step.get("with") or {}
    if "install-engine" in uses:
        return True
    if not using.get("engine-version"):
        return False
    if "install-shared-dependencies" in uses:
        return True
    action = local_action(uses)
    if not action or depth == 0:
        return False
    with open(action) as handle:
        wrapped = yaml.safe_load(handle) or {}
    return any(installs_engine(inner, depth - 1) for inner in ((wrapped.get("runs") or {}).get("steps") or []))


def first_field(entry, names):
    """The first of these fields the entry defines, mirroring the || fallback."""
    for name in names:
        value = field(entry, name)
        if value:
            return value
    return None


def from_matrix(workflow, path, job_id, job, runner_refs, target_refs, state):
    """Check a job whose runner and target both come from one matrix key."""
    where, key = f"{path}:{job_id}", runner_refs[0][0]
    runner_fields = [name for _, name in runner_refs]
    target_fields = [name for _, name in target_refs]
    entries = field(field(job.get("strategy") or {}, "matrix"), key)

    if isinstance(entries, list):
        pairs = [(first_field(e, runner_fields), first_field(e, target_fields)) for e in entries if isinstance(e, dict)]
        state.notes.append(f"{where}: {len(pairs)} pair(s) from the inline {key} matrix")
        return mismatches(where, pairs, state.declared)

    source = generating_job(workflow, entries)
    if not source or not reads_matrix_file(workflow, source):
        return [f"{where} builds its {key} matrix in a way this check cannot resolve"]
    # The matrix comes from build-matrix.json, so a runner and a target read from one
    # entry agree by construction. Fields belonging to other entries do not.
    foreign = [
        name
        for name, allowed in [(n, RUNNER_FIELDS) for n in runner_fields] + [(n, TARGET_FIELDS) for n in target_fields]
        if name.lower() not in allowed and name.lower() in state.fields
    ]
    if foreign:
        return [f"{where} reads {', '.join(foreign)}, which is not the runner or target of one entry"]
    state.notes.append(f"{where}: {key} matrix comes from build-matrix.json via {source}")
    return []


def mismatches(where, pairs, declared):
    found = []
    for runner, target in pairs:
        if runner is None or target is None:
            found.append(f"{where} has a matrix entry without both a runner and a target")
        elif target in declared and labels(runner) not in declared[target]:
            found.append(f"{where} runs {target} on {runner}, which build-matrix.json does not declare for it")
    return found


def check_job(workflow, path, job_id, job, target, state):
    """Every mismatch in one engine-installing job, or a failure to resolve it."""
    where, runner = f"{path}:{job_id}", job.get("runs-on")
    runner_refs, target_refs = references(runner), references(target)

    if runner_refs and target_refs and len({key for key, _ in runner_refs + target_refs}) == 1:
        return from_matrix(workflow, path, job_id, job, runner_refs, target_refs, state)
    candidates = [runner] if OPEN not in str(runner) else literal_runners(str(runner))
    if candidates is None:
        return [f"{where} picks its runner with an expression this check cannot resolve: {runner}"]
    if OPEN in str(target):
        return [f"{where} takes its target from an expression this check cannot resolve: {target}"]
    state.notes.append(f"{where}: {len(candidates)} runner(s) for target {target}")
    return mismatches(where, [(candidate, target) for candidate in candidates], state.declared)


class State:
    def __init__(self):
        self.declared, self.fields = load_matrix()
        self.notes = []


def main():
    state, problems = State(), []
    for path in sorted(glob.glob(WORKFLOWS)):
        with open(path) as handle:
            workflow = yaml.safe_load(handle) or {}
        for job_id, job in (workflow.get("jobs") or {}).items():
            for step in job.get("steps") or []:
                if installs_engine(step):
                    target = (step.get("with") or {}).get("target")
                    problems += check_job(workflow, path, job_id, job, target, state)

    print("\n".join(f"  {note}" for note in state.notes))
    if problems:
        print("ERROR: engine installs on runners that build-matrix.json does not declare:")
        print("\n".join(f"  {problem}" for problem in problems))
        return 1
    print(f"OK: {len(state.notes)} engine install(s) checked, every runner is declared for its target")
    return 0


if __name__ == "__main__":
    sys.exit(main())
