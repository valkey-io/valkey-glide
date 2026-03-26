#!/bin/bash
#
# Sets up a stress test node: installs kind, creates cluster, builds image, deploys pods.
# Usage: ./setup_stress_node.sh <num_pods> <host> <start_port>
#
set +e

NUM_PODS=${1:-15}
CLUSTER_HOST=${2:-crr-cluster.glide.cross.region}
START_PORT=${3:-6669}

echo "=============================================="
echo "Setting up stress test node"
echo "Pods: $NUM_PODS"
echo "Host: $CLUSTER_HOST"
echo "=============================================="

# Install kind if not present
if ! command -v kind &>/dev/null; then
    echo "Installing kind..."
    curl -Lo /usr/local/bin/kind https://kind.sigs.k8s.io/dl/v0.27.0/kind-linux-amd64
    chmod +x /usr/local/bin/kind
fi

# Install kubectl if not present
if ! command -v kubectl &>/dev/null; then
    echo "Installing kubectl..."
    curl -LO "https://dl.k8s.io/release/$(curl -L -s https://dl.k8s.io/release/stable.txt)/bin/linux/amd64/kubectl"
    chmod +x kubectl
    mv kubectl /usr/local/bin/
fi

echo "kind: $(kind version)"
echo "kubectl: $(kubectl version --client --short 2>/dev/null || kubectl version --client)"

# Create kind cluster with 3 workers
if kind get clusters 2>/dev/null | grep -q "glide-stress"; then
    echo "Cluster already exists"
else
    echo "Creating kind cluster..."
    cat << 'EOF' > /tmp/kind-config.yaml
kind: Cluster
apiVersion: kind.x-k8s.io/v1alpha4
nodes:
  - role: control-plane
  - role: worker
  - role: worker
  - role: worker
EOF
    kind create cluster --name glide-stress --config /tmp/kind-config.yaml
fi

# Build and load Docker image
echo "Building Docker image..."
if [ -d /home/ubuntu/ec2-deploy ]; then
    sudo docker build --no-cache -t glide-bench:latest /home/ubuntu/ec2-deploy/
    kind load docker-image glide-bench:latest --name glide-stress
else
    echo "ERROR: /home/ubuntu/ec2-deploy not found. Please transfer the build package first."
    exit 1
fi

# Generate and apply pod manifests
echo "Deploying $NUM_PODS pods..."
WORKERS=($(kubectl get nodes --no-headers -l '!node-role.kubernetes.io/control-plane' -o name | sed 's|node/||'))
echo "Workers: ${WORKERS[*]}"

mkdir -p /home/ubuntu/k8s-manifests

# 1 writer + (NUM_PODS-1) deleters
cat << EOF > /home/ubuntu/k8s-manifests/writer.yaml
apiVersion: v1
kind: Pod
metadata:
  name: stress-writer
spec:
  hostNetwork: true
  dnsPolicy: ClusterFirstWithHostNet
  nodeName: ${WORKERS[0]}
  containers:
    - name: benchmark
      image: glide-bench:latest
      imagePullPolicy: Never
      resources:
        requests: { cpu: "2", memory: "2Gi" }
        limits: { cpu: "2", memory: "4Gi" }
      env:
        - name: TOKIO_CONSOLE_PORT
          value: "$START_PORT"
        - name: GLIDE_LOG_DIR
          value: "/logs"
      args: ["--clients", "glide", "--clientCount", "1", "--host", "$CLUSTER_HOST",
             "--port", "6379", "--tls", "--clusterModeEnabled", "--operations", "write",
             "--concurrentTasks", "60", "--requestTimeout", "50", "--duration", "86400",
             "--dataSize", "100", "--metricsDir", "/metrics", "--metricsInterval", "60"]
      volumeMounts:
        - { name: metrics, mountPath: /metrics }
        - { name: logs, mountPath: /logs }
  volumes:
    - { name: metrics, hostPath: { path: /tmp/metrics-writer, type: DirectoryOrCreate } }
    - { name: logs, hostPath: { path: /tmp/logs-writer, type: DirectoryOrCreate } }
  restartPolicy: Never
EOF

for i in $(seq 1 $((NUM_PODS - 1))); do
    WORKER_IDX=$(( (i - 1) % ${#WORKERS[@]} ))
    PORT=$((START_PORT + i))
    cat << EOF > /home/ubuntu/k8s-manifests/del-${i}.yaml
apiVersion: v1
kind: Pod
metadata:
  name: stress-del-${i}
spec:
  hostNetwork: true
  dnsPolicy: ClusterFirstWithHostNet
  nodeName: ${WORKERS[$WORKER_IDX]}
  containers:
    - name: benchmark
      image: glide-bench:latest
      imagePullPolicy: Never
      resources:
        requests: { cpu: "2", memory: "2Gi" }
        limits: { cpu: "2", memory: "4Gi" }
      env:
        - name: TOKIO_CONSOLE_PORT
          value: "$PORT"
        - name: GLIDE_LOG_DIR
          value: "/logs"
      args: ["--clients", "glide", "--clientCount", "1", "--host", "$CLUSTER_HOST",
             "--port", "6379", "--tls", "--clusterModeEnabled", "--operations", "delete",
             "--concurrentTasks", "60", "--requestTimeout", "50", "--duration", "86400",
             "--dataSize", "100", "--metricsDir", "/metrics", "--metricsInterval", "60"]
      volumeMounts:
        - { name: metrics, mountPath: /metrics }
        - { name: logs, mountPath: /logs }
  volumes:
    - { name: metrics, hostPath: { path: "/tmp/metrics-del-${i}", type: DirectoryOrCreate } }
    - { name: logs, hostPath: { path: "/tmp/logs-del-${i}", type: DirectoryOrCreate } }
  restartPolicy: Never
EOF
done

kubectl delete pod --all 2>/dev/null
kubectl apply -f /home/ubuntu/k8s-manifests/
echo ""
echo "=============================================="
echo "Deployed. Checking pods..."
echo "=============================================="
sleep 5
kubectl get pods -o wide
