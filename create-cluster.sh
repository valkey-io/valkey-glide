#! /bin/bash

set -x

SCRIPT_FOLDER=$(dirname $(readlink -f ${BASH_SOURCE[0]}))
NODES_FOLDER=$SCRIPT_FOLDER/redis-cluster-nodes
TARGET_FOLDER=$NODES_FOLDER/$IDENTIFIER
mkdir -p $TARGET_FOLDER

if [ -z ${START_PORT} ]; then
    START_PORT=16379
fi
if [ -z ${NODE_COUNT} ]; then
    NODE_COUNT=6
fi
if [ -z ${REPLICA_COUNT} ]; then
    REPLICA_COUNT=1
fi

END_PORT=$((START_PORT + NODE_COUNT - 1))

if [ "$USE_TLS" == "1" ]; then
    TLS_CONNECTION_COMMAND="--tls --insecure"
fi

function create_servers() {
    if [ "$USE_TLS" == "1" ]; then
        $SCRIPT_FOLDER/gen-test-certs.sh
        TLS_CREATION_COMMAND="--tls-cluster yes \
            --tls-replication yes \
            --tls-cert-file ${TARGET_FOLDER}/tls/redis.crt \
            --tls-key-file ${TARGET_FOLDER}/tls/redis.key \
            --tls-ca-cert-file ${TARGET_FOLDER}/tls/ca.crt \
            --tls-auth-clients no \
            --bind 127.0.0.1"
    fi

    for PORT in `seq ${START_PORT} ${END_PORT}`; do
        if [ "$USE_TLS" == "1" ]; then
            PORT_CONNECTION="--tls-port $PORT --port 0"
        else
            PORT_CONNECTION="--port $PORT"
        fi

        echo "STARTING: ${PORT}"
        touch $TARGET_FOLDER/${PORT}.log
        redis-server \
            --daemonize yes \
            $PORT_CONNECTION \
            --cluster-enabled yes \
            --cluster-config-file nodes-${PORT}.conf \
            --cluster-node-timeout 2000 \
            --dir $TARGET_FOLDER/ \
            --dbfilename dump-${PORT}.rdb \
            --logfile ${PORT}.log \
            $TLS_CREATION_COMMAND

        if [[$? -ne 0]]; then
            exit $?
        fi
    done
}

function wait_for_servers_to_be_ready() {
    for PORT in `seq ${START_PORT} ${END_PORT}`; do
        RESULT=""
        until [[ "$RESULT" == "PONG" ]];
        do
            sleep 0.01
            RESULT=$(redis-cli -p $PORT $TLS_CONNECTION_COMMAND PING)
        done
    done
}

function create_cluster() {
    HOSTS=""
    for PORT in `seq ${START_PORT} ${END_PORT}`; do
        HOSTS="$HOSTS 127.0.0.1:$PORT"
    done
    redis-cli --cluster create $HOSTS \
        --cluster-replicas $REPLICA_COUNT \
        --cluster-yes \
        $TLS_CONNECTION_COMMAND

    if [[$? -ne 0]]; then
        exit $?
    fi
}

function wait_for_cluster_to_be_ready() {
    for PORT in `seq ${START_PORT} ${END_PORT}`; do
        FOUND_NODE_COUNT=$(redis-cli -p $PORT $TLS_CONNECTION_COMMAND CLUSTER NODES | wc -l)
        until [[ $FOUND_NODE_COUNT -eq $NODE_COUNT ]];
        do
            sleep 0.1
            FOUND_NODE_COUNT=$(redis-cli -p $PORT $TLS_CONNECTION_COMMAND CLUSTER NODES | wc -l)
        done
    done

    # Couldn't get this to work :(
    # for PORT in `seq ${START_PORT} ${END_PORT}`; do
    #     OUTPUT=""
    #     until [ "$OUTPUT" == *"cluster_slots_ok:16384"* ];
    #     do
    #         sleep 0.1
    #         OUTPUT=$(redis-cli -p $PORT $TLS_CONNECTION_COMMAND CLUSTER INFO)
    #     done
    # done
}

if [ "$1" == "start" ]; then
    create_servers
    wait_for_servers_to_be_ready
    create_cluster
    wait_for_cluster_to_be_ready

    echo "STARTED"
    exit 0
fi

if [ "$1" == "stop" ]
then
    for PORT in `seq ${START_PORT} ${END_PORT}`; do
        echo "Stopping $PORT"
        redis-cli -p $PORT shutdown nosave &
    done
    rm -rf $TARGET_FOLDER*
    exit 0
fi
