#!/bin/bash
# Lifecycle for the rdma-pair CloudFormation stack.
#   plan    build a change set and print what it would do (nothing is created
#           except the change set; a new stack sits in REVIEW_IN_PROGRESS)
#   apply   execute the change set, building one first if `plan` was not run,
#           then stream stack events until the stack settles
#   status  stack state plus instance states
#   stop    stop both instances (volumes are kept and still billed)
#   start   start both instances
#   down    delete the stack and stream events until it is gone
# Extra arguments to plan and apply are CloudFormation parameter overrides,
# e.g. `plan InstanceType=c8gn.48xlarge`.
#
# Environment:
#   RDMA_TEMPLATE_URL  S3 HTTPS URL of the infrastructure-owned pair template;
#                      required for plan/apply, not for status/down
#   AWS_REGION    default us-east-1
#   RDMA_STACK    stack name, default rdma-pair
#   RDMA_OWNER    Owner tag and parameter, default the local user name
#   RDMA_VPC_ID   VpcId parameter when not given on the command line
#   RDMA_INSTANCE_PROFILE_ARN  InstanceProfileArn parameter when not given on
#                 the command line; the stack is created without IAM capabilities
#   RDMA_SUBNETS  comma-separated SubnetId candidates; apply tries them in
#                 order when a create fails on InsufficientInstanceCapacity
set -euo pipefail

export AWS_REGION=${AWS_REGION:-us-east-1}
export AWS_PAGER=""   # tables would otherwise open in less and block the script on a terminal
STACK=${RDMA_STACK:-rdma-pair}
CHANGE_SET=$STACK-plan
RDMA_OWNER=${RDMA_OWNER:-$(id -un)}

stack_status() {
  local output
  if output=$(aws cloudformation describe-stacks --stack-name "$STACK" --query 'Stacks[0].StackStatus' --output text 2>&1); then
    echo "$output"
  elif [[ "$output" == *"(ValidationError)"* && "$output" == *"does not exist"* ]]; then
    echo NONE
  else
    echo "$output" >&2
    return 1
  fi
}

instances() {
  aws cloudformation describe-stacks --stack-name "$STACK" \
    --query "Stacks[0].Outputs[?OutputKey=='ServerInstanceId'||OutputKey=='ClientInstanceId'].OutputValue" --output text
}

# Prints each new stack event as it lands and returns when the stack reaches a
# terminal state: 0 on *_COMPLETE other than a rollback, 1 otherwise. The AWS
# CLI waiters are silent and `stack-update-complete` never ends on
# ROLLBACK_COMPLETE, which is how a failed create once polled quietly for an hour.
stream_events() {
  local since=$1 status
  while :; do
    status=$(stack_status) || return 1
    # A deleted stack has no events to describe; that is the exit, not an error.
    aws cloudformation describe-stack-events --stack-name "$STACK" \
      --query "reverse(StackEvents[?Timestamp>='$since'])[].[EventId,Timestamp,LogicalResourceId,ResourceStatus,ResourceStatusReason]" \
      --output text 2>/dev/null | while IFS=$'\t' read -r id ts res st reason; do
        grep -qF "$id" "$seen_file" && continue
        echo "$id" >> "$seen_file"
        printf '%s  %-16s %-28s %s\n' "${ts:11:8}" "$res" "$st" "${reason/None/}"
      done || true
    case "$status" in
      *_IN_PROGRESS) sleep 10 ;;
      NONE|DELETE_COMPLETE) echo "stack: $status"; return 0 ;;
      ROLLBACK_COMPLETE|UPDATE_ROLLBACK_COMPLETE|*_FAILED) echo "stack: $status"; return 1 ;;
      *) echo "stack: $status"; return 0 ;;
    esac
  done
}
seen_file=$(mktemp)
trap 'rm -f "$seen_file"' EXIT

has_param() {
  local key=$1 kv; shift
  for kv in "$@"; do [ "${kv%%=*}" = "$key" ] && return 0; done
  return 1
}

# Subnets in the VPC that give instances a public IP, as "zone subnet-id" lines.
# Only a hint for the operator: a private subnet behind a NAT gateway works too
# (AssignPublicIp=false).
public_subnets() {
  aws ec2 describe-subnets --filters "Name=vpc-id,Values=$1" Name=map-public-ip-on-launch,Values=true \
    --query 'sort_by(Subnets, &AvailabilityZone)[].[AvailabilityZone,SubnetId]' --output text 2>/dev/null
}

# RDMA_SUBNETS one per line, with blanks and spaces dropped so a stray comma
# cannot send an empty SubnetId or repeat the first entry.
subnet_list() {
  tr ',' '\n' <<<"${RDMA_SUBNETS:-}" | tr -d ' \t' | sed '/^$/d'
}

# A stack in one of these states holds no resources, so the next change set is a CREATE.
stack_is_new() {
  case "$(stack_status)" in NONE|REVIEW_IN_PROGRESS|ROLLBACK_COMPLETE) return 0 ;; *) return 1 ;; esac
}

# Prints the parameters for a change set of the given type, one per line:
# Key=Value, or Key=@previous to keep the stack's current value. The template
# has no defaults for Owner, VpcId and SubnetId, so on a CREATE a missing one
# stops here with a hint before any change set exists. On an UPDATE, a value
# not given explicitly is kept rather than taken from the environment, because
# a changed SubnetId or Owner would replace both hosts or retag the stack.
complete_params() {
  local type=$1 kv; shift
  for kv in "$@"; do echo "$kv"; done
  if [ "$type" = UPDATE ]; then
    for kv in Owner VpcId SubnetId InstanceProfileArn; do has_param "$kv" "$@" || echo "$kv=@previous"; done
    return 0
  fi
  has_param Owner "$@" || echo "Owner=$RDMA_OWNER"
  if ! has_param InstanceProfileArn "$@"; then
    [ -n "${RDMA_INSTANCE_PROFILE_ARN:-}" ] || { echo "InstanceProfileArn=<arn> or RDMA_INSTANCE_PROFILE_ARN is required" >&2; exit 2; }
    echo "InstanceProfileArn=$RDMA_INSTANCE_PROFILE_ARN"
  fi
  if ! has_param VpcId "$@"; then
    [ -n "${RDMA_VPC_ID:-}" ] || { echo "VpcId=<vpc-id> or RDMA_VPC_ID is required" >&2; exit 2; }
    echo "VpcId=$RDMA_VPC_ID"
  fi
  if ! has_param SubnetId "$@"; then
    local first
    first=$(subnet_list | head -1)
    if [ -n "$first" ]; then
      echo "SubnetId=$first"
    else
      local vpc=${RDMA_VPC_ID:-}
      for kv in "$@"; do [ "${kv%%=*}" = VpcId ] && vpc=${kv#*=}; done
      { echo "SubnetId=<subnet-id> or RDMA_SUBNETS is required; subnets in $vpc that assign public IPs:"
        public_subnets "$vpc" | sed 's/^/  /'; } >&2
      exit 2
    fi
  fi
}

plan() {
  if [ -z "${RDMA_TEMPLATE_URL:-}" ]; then
    echo "Set RDMA_TEMPLATE_URL to the infrastructure-owned pair template URL" >&2
    exit 2
  fi
  # A stack that was planned but never applied is REVIEW_IN_PROGRESS and still takes a CREATE change set.
  # A failed create ends in ROLLBACK_COMPLETE, holds no resources, and must be deleted before a new create.
  local state type
  state=$(stack_status) || exit 1
  case "$state" in
    NONE|REVIEW_IN_PROGRESS|ROLLBACK_COMPLETE) type=CREATE ;;
    CREATE_FAILED)
      echo "stack $STACK is CREATE_FAILED and still holds its resources; read the hosts' logs, then run '$0 down' before a new create" >&2
      exit 1 ;;
    *_IN_PROGRESS) echo "stack $STACK is $state; wait for it to settle" >&2; exit 1 ;;
    *) type=UPDATE ;;
  esac
  # Parameters are checked before the ROLLBACK_COMPLETE delete below, so a
  # missing one does not cost the stack.
  local params=() kv completed
  completed=$(complete_params "$type" "$@") || exit $?
  while IFS= read -r kv; do
    case "$kv" in
      *=@previous) params+=("ParameterKey=${kv%%=*},UsePreviousValue=true") ;;
      *) params+=("ParameterKey=${kv%%=*},ParameterValue=${kv#*=}") ;;
    esac
  done <<<"$completed"
  if [ "$state" = ROLLBACK_COMPLETE ]; then
    echo "stack $STACK is ROLLBACK_COMPLETE from a failed create; deleting it before planning a new create"
    aws cloudformation delete-stack --stack-name "$STACK"
    aws cloudformation wait stack-delete-complete --stack-name "$STACK"
  fi
  aws cloudformation delete-change-set --stack-name "$STACK" --change-set-name "$CHANGE_SET" >/dev/null 2>&1 || true
  # On a failed create, keep the half-built stack instead of rolling back so
  # /var/log/rdma-bootstrap.log on the hosts can be read over SSM. `down`
  # removes it afterwards.
  local on_failure=()
  [ "$type" = CREATE ] && on_failure=(--on-stack-failure DO_NOTHING)
  aws cloudformation create-change-set --stack-name "$STACK" --change-set-name "$CHANGE_SET" \
    --change-set-type "$type" --template-url "$RDMA_TEMPLATE_URL" \
    --tags "Key=Owner,Value=$RDMA_OWNER" Key=Purpose,Value=rdma-test \
    ${on_failure[@]+"${on_failure[@]}"} \
    --parameters "${params[@]}" >/dev/null || exit 1
  aws cloudformation wait change-set-create-complete --stack-name "$STACK" --change-set-name "$CHANGE_SET" || true
  local cs_status
  cs_status=$(aws cloudformation describe-change-set --stack-name "$STACK" --change-set-name "$CHANGE_SET" \
    --query '[Status,StatusReason]' --output text)
  echo "change set: $cs_status"
  aws cloudformation describe-change-set --stack-name "$STACK" --change-set-name "$CHANGE_SET" \
    --query 'Changes[].ResourceChange.[Action,LogicalResourceId,ResourceType,Replacement]' --output table
  case "$cs_status" in CREATE_COMPLETE*) ;; *) exit 1 ;; esac
}

down() {
  local since
  since=$(date -u +%Y-%m-%dT%H:%M:%S)
  aws cloudformation delete-stack --stack-name "$STACK"
  stream_events "$since"
}

# True when an event since $1 names EC2 capacity as the failure. The error code
# is matched by name and, because newer handler messages may carry only the
# prose, also by its two key words.
capacity_failure() {
  local n reason="(ResourceStatusReason || '')"
  n=$(aws cloudformation describe-stack-events --stack-name "$STACK" \
    --query "length(StackEvents[?Timestamp>='$1' && (contains($reason, 'InsufficientInstanceCapacity') || (contains($reason, 'sufficient') && contains($reason, 'capacity')))])" \
    --output text 2>/dev/null || echo 0)
  [ "$n" -gt 0 ]
}

# One plan-and-execute attempt; returns stream_events' status and sets
# attempt_since to the attempt's start. A failed plan exits the script.
# Every command here is checked explicitly: the caller tests this function's
# status, and bash ignores `set -e` inside anything run as a condition.
apply_once() {
  # Executing a change set consumes it, so a fresh one is needed for every
  # apply. UNAVAILABLE (a change set that failed to build) must not match.
  if ! aws cloudformation describe-change-set --stack-name "$STACK" --change-set-name "$CHANGE_SET" \
       --query ExecutionStatus --output text 2>/dev/null | grep -qx AVAILABLE; then
    plan "$@" || exit 1
  fi
  attempt_since=$(date -u +%Y-%m-%dT%H:%M:%S)
  aws cloudformation execute-change-set --stack-name "$STACK" --change-set-name "$CHANGE_SET" || return 1
  stream_events "$attempt_since"
}

case "${1:-}" in
  plan)
    shift
    plan "$@"
    ;;
  apply)
    shift
    # On a new stack with RDMA_SUBNETS set and no SubnetId on the command
    # line, each listed subnet is tried in turn, moving on only when the
    # CREATE failed for capacity in that zone. An existing stack keeps its
    # subnet and is never deleted on failure; any other failure stops with the
    # stack kept.
    subnets=("")
    if ! has_param SubnetId "$@" && stack_is_new && [ -n "$(subnet_list)" ]; then
      subnets=()
      while IFS= read -r s; do subnets+=("$s"); done < <(subnet_list)
    fi
    attempt_since=
    for i in "${!subnets[@]}"; do
      extra=()
      [ -n "${subnets[$i]}" ] && extra=("SubnetId=${subnets[$i]}")
      if apply_once "$@" ${extra[@]+"${extra[@]}"}; then
        aws cloudformation describe-stacks --stack-name "$STACK" --query 'Stacks[0].Outputs' --output table
        exit 0
      fi
      if [ "$(stack_status)" = CREATE_FAILED ] && capacity_failure "$attempt_since" \
         && [ "$i" -lt $((${#subnets[@]} - 1)) ]; then
        echo "no capacity in ${subnets[$i]}; deleting the failed stack and trying ${subnets[$((i + 1))]}"
        down || { echo "delete of $STACK failed; stack kept, not retrying" >&2; exit 1; }
        continue
      fi
      exit 1
    done
    ;;
  status)
    stack_status
    ids=$(instances)
    # A CREATE_FAILED stack has no outputs; fall back to the instance tags.
    if [ -z "$ids" ] || [ "$ids" = None ]; then
      ids=$(aws ec2 describe-instances --filters Name=tag:aws:cloudformation:stack-name,Values="$STACK" \
        Name=instance-state-name,Values=pending,running,stopping,stopped \
        --query 'Reservations[].Instances[].InstanceId' --output text)
    fi
    if [ -n "$ids" ] && [ "$ids" != None ]; then
      aws ec2 describe-instances --instance-ids $ids \
        --query 'Reservations[].Instances[].[Tags[?Key==`Name`]|[0].Value,InstanceId,State.Name,PrivateIpAddress]' --output table
    fi
    ;;
  stop)  aws ec2 stop-instances --instance-ids $(instances) --output table ;;
  start) aws ec2 start-instances --instance-ids $(instances) --output table ;;
  down)  down ;;
  *) echo "usage: $0 plan [Key=Value ...] | apply [Key=Value ...] | status | stop | start | down" >&2; exit 2 ;;
esac
