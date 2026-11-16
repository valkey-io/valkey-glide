#!/bin/bash
# Run one host/ script on rdma-server or rdma-client through SSM and print its output.
# Usage: bin/run-on.sh <server|client> host/<script>.sh [KEY=value ...]
# The script runs as root on the host with SERVER_IP, CLIENT_IP and the KEY=value
# pairs set. Variables in this shell's environment do not reach the host.
# AWS_REGION and RDMA_STACK select the pair, as in rdma-pair.sh.
set -euo pipefail

export AWS_REGION=${AWS_REGION:-us-east-1}
export AWS_PAGER=""
STACK=${RDMA_STACK:-rdma-pair}
TIMEOUT=3600   # the valkey + module build is the long one

role=${1:?usage: $0 <server|client> host/<script>.sh [KEY=value ...]}
script=${2:?usage: $0 <server|client> host/<script>.sh [KEY=value ...]}
shift 2
[ -r "$script" ] || { echo "no such script: $script" >&2; exit 2; }
for kv in "$@"; do
  case "$kv" in
    [A-Za-z_]*=*) ;;
    *) echo "override '$kv' is not KEY=value; overrides go after the script name, not in the environment" >&2; exit 2 ;;
  esac
done

# The instances are found by tag rather than stack output, so a CREATE_FAILED
# stack (which has no outputs) is still reachable for diagnosis.
instance_of() {
  aws ec2 describe-instances \
    --filters "Name=tag:aws:cloudformation:stack-name,Values=$STACK" "Name=tag:Name,Values=rdma-$1" \
      Name=instance-state-name,Values=running \
    --query 'Reservations[0].Instances[0].[InstanceId,PrivateIpAddress]' --output text
}
case "$role" in
  server|client) ;;
  *) echo "role must be server or client" >&2; exit 2 ;;
esac
read -r server_id server_ip <<<"$(instance_of server)"
read -r client_id client_ip <<<"$(instance_of client)"
[ "$server_id" != None ] && [ "$client_id" != None ] || { echo "both rdma-server and rdma-client must be running" >&2; exit 1; }
instance=$server_id; [ "$role" = client ] && instance=$client_id

# The script travels inside the command parameters, the way the valkey-glide
# Windows orchestrator ships cluster_manager.py: gzip + base64, decoded on the
# host. A script that needs sibling files names them in a `# ships: a b` line
# and they travel the same way, landing beside it in /var/log/rdma.
name=$(basename "$script")
ship_cmds=()
for extra in $(sed -nE 's/^# ships: *//p' "$script"); do
  path=$(dirname "$script")/$extra
  [ -r "$path" ] || { echo "$script ships $extra, which is missing" >&2; exit 2; }
  ship_cmds+=("echo '$(gzip -c "$path" | base64 | tr -d '\n')' | base64 -d | gunzip > /var/log/rdma/$extra")
done
payload=$(gzip -c "$script" | base64 | tr -d '\n')
env_pairs="SERVER_IP=$server_ip CLIENT_IP=$client_ip $*"
params=$(python3 -c '
import json, sys
print(json.dumps({"commands": sys.argv[2:], "executionTimeout": [sys.argv[1]]}))' \
  "$TIMEOUT" \
  "mkdir -p /var/log/rdma && echo '$payload' | base64 -d | gunzip > /var/log/rdma/$name" \
  ${ship_cmds[@]+"${ship_cmds[@]}"} \
  "cd /root && set -o pipefail && env $env_pairs bash /var/log/rdma/$name 2>&1 | tee /var/log/rdma/${name%.sh}.log")

echo "== $role ($instance): $name"
command_id=$(aws ssm send-command --instance-ids "$instance" --document-name AWS-RunShellScript \
  --comment "rdma-test $name on $role" --timeout-seconds 600 --parameters "$params" \
  --query Command.CommandId --output text)

status=Pending
while [ "$status" = Pending ] || [ "$status" = InProgress ] || [ "$status" = Delayed ]; do
  sleep 5
  status=$(aws ssm get-command-invocation --command-id "$command_id" --instance-id "$instance" \
    --query Status --output text 2>/dev/null || echo Pending)
done

# StandardOutputContent is capped at 24 000 characters; the full log stays on
# the host at /var/log/rdma/<script>.log.
aws ssm get-command-invocation --command-id "$command_id" --instance-id "$instance" \
  --query StandardOutputContent --output text
aws ssm get-command-invocation --command-id "$command_id" --instance-id "$instance" \
  --query StandardErrorContent --output text | sed '/^None$/d; s/^/stderr: /'
echo "== $status"
[ "$status" = Success ]
