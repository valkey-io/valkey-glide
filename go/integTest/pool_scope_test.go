// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

package integTest

import (
	"context"
	"fmt"
	"strconv"
	"strings"
	"sync"
	"testing"
	"time"

	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
	glide "github.com/valkey-io/valkey-glide/go/v2"
	"github.com/valkey-io/valkey-glide/go/v2/config"
	"github.com/valkey-io/valkey-glide/go/v2/models"
)

// ═══════════════════════════════════════════════════════════════════════════════
// Helpers
// ═══════════════════════════════════════════════════════════════════════════════

func standaloneConfig() *config.ClientConfiguration {
	host := "localhost"
	port := 6379
	if standaloneHosts != nil && *standaloneHosts != "" {
		parts := strings.SplitN(*standaloneHosts, ",", 2)
		hostPort := strings.SplitN(parts[0], ":", 2)
		if len(hostPort) == 2 {
			host = hostPort[0]
			if p, err := strconv.Atoi(hostPort[1]); err == nil {
				port = p
			}
		}
	}
	return config.NewClientConfiguration().
		WithAddress(&config.NodeAddress{Host: host, Port: port}).
		WithRequestTimeout(5000 * time.Millisecond)
}

// standaloneConfigForClusterNode returns a standalone ClientConfiguration
// pointed at the first cluster node. This allows using ScopedConnection against
// a cluster node for single-slot operations with hash-tagged keys.
func standaloneConfigForClusterNode() *config.ClientConfiguration {
	host := "localhost"
	port := 7000
	if clusterHosts != nil && *clusterHosts != "" {
		parts := strings.SplitN(*clusterHosts, ",", 2)
		hostPort := strings.SplitN(parts[0], ":", 2)
		if len(hostPort) == 2 {
			host = hostPort[0]
			if p, err := strconv.Atoi(hostPort[1]); err == nil {
				port = p
			}
		}
	}
	return config.NewClientConfiguration().
		WithAddress(&config.NodeAddress{Host: host, Port: port}).
		WithRequestTimeout(5000 * time.Millisecond)
}

func compressedStandaloneConfig() *config.ClientConfiguration {
	compressionConfig := config.NewCompressionConfiguration().
		WithBackend(config.ZSTD).
		WithMinCompressionSize(64)
	cfg := standaloneConfig()
	return cfg.WithCompressionConfiguration(compressionConfig)
}

func compressedClusterNodeConfig() *config.ClientConfiguration {
	compressionConfig := config.NewCompressionConfiguration().
		WithBackend(config.ZSTD).
		WithMinCompressionSize(64)
	cfg := standaloneConfigForClusterNode()
	return cfg.WithCompressionConfiguration(compressionConfig)
}

func clusterConfig() *config.ClusterClientConfiguration {
	host := "localhost"
	port := 7000
	if clusterHosts != nil && *clusterHosts != "" {
		parts := strings.SplitN(*clusterHosts, ",", 2)
		hostPort := strings.SplitN(parts[0], ":", 2)
		if len(hostPort) == 2 {
			host = hostPort[0]
			if p, err := strconv.Atoi(hostPort[1]); err == nil {
				port = p
			}
		}
	}
	return config.NewClusterClientConfiguration().
		WithAddress(&config.NodeAddress{Host: host, Port: port}).
		WithRequestTimeout(5000 * time.Millisecond)
}

func clusterAvailable() bool {
	return clusterHosts != nil && *clusterHosts != ""
}

// skipIfNoStandaloneEndpoints skips the test if no --standalone-endpoints flag
func getClusterServerVersion(t *testing.T, client *glide.ClusterClient) string {
	ctx := context.Background()
	info, err := client.CustomCommand(ctx, []string{"INFO", "SERVER"})
	if err != nil {
		t.Skipf("Cannot get server version: %v", err)
		return "0.0.0"
	}
	var infoStr string
	if info.IsSingleValue() {
		infoStr = fmt.Sprintf("%v", info.SingleValue())
	} else {
		for _, v := range info.MultiValue() {
			infoStr = fmt.Sprintf("%v", v)
			break
		}
	}
	for _, line := range strings.Split(infoStr, "\n") {
		line = strings.TrimSpace(line)
		if strings.HasPrefix(line, "valkey_version:") {
			return strings.TrimPrefix(line, "valkey_version:")
		}
	}
	for _, line := range strings.Split(infoStr, "\n") {
		line = strings.TrimSpace(line)
		if strings.HasPrefix(line, "redis_version:") {
			return strings.TrimPrefix(line, "redis_version:")
		}
	}
	return "0.0.0"
}

func versionLessThan(ver string, target string) bool {
	verParts := strings.Split(ver, ".")
	targetParts := strings.Split(target, ".")
	for i := 0; i < len(targetParts) && i < len(verParts); i++ {
		v, _ := strconv.Atoi(verParts[i])
		tv, _ := strconv.Atoi(targetParts[i])
		if v < tv {
			return true
		}
		if v > tv {
			return false
		}
	}
	return false
}

// scopeModes returns the table of modes for parameterized scope tests.
func scopeModes() []struct {
	name    string
	cluster bool
} {
	return []struct {
		name    string
		cluster bool
	}{
		{name: "standalone", cluster: false},
		{name: "cluster", cluster: true},
	}
}

// scopeTestKey returns a unique key, hash-tagged in cluster mode.
func scopeTestKey(prefix string, cluster bool) string {
	if cluster {
		return fmt.Sprintf("{scope-test}-%s-%d", prefix, time.Now().UnixNano())
	}
	return fmt.Sprintf("%s-%d", prefix, time.Now().UnixNano())
}

// scopeTestClient is an interface satisfied by both Client and ClusterClient
// for scope testing purposes.
type scopeTestClient interface {
	ScopedConnection(ctx context.Context, timeout time.Duration) (*glide.IsolatedScope, error)
	Set(ctx context.Context, key string, value string) (string, error)
	Get(ctx context.Context, key string) (models.Result[string], error)
	Del(ctx context.Context, keys []string) (int64, error)
	Close()
}

// newScopeClient creates a Client or ClusterClient appropriate for the mode.
func newScopeClient(t *testing.T, cluster bool) scopeTestClient {
	t.Helper()
	if cluster {
		client, err := glide.NewClusterClient(clusterConfig())
		require.NoError(t, err)
		return client
	}
	client, err := glide.NewClient(standaloneConfig())
	require.NoError(t, err)
	return client
}

// skipMode skips the subtest if the required endpoints aren't available.
func skipMode(t *testing.T, cluster bool) {
	t.Helper()
	if cluster {
		if !clusterAvailable() {
			t.Skip("No cluster endpoints configured")
		}
	} else {
		if standaloneHosts == nil || *standaloneHosts == "" {
			t.Skip("No --standalone-endpoints provided")
		}
	}
}

// ═══════════════════════════════════════════════════════════════════════════════
// Feature 1: ClientPool tests (parameterized: standalone + cluster)
// ═══════════════════════════════════════════════════════════════════════════════

// newPool creates a ClientPool appropriate for the mode.
func newPool(t *testing.T, cluster bool, poolCfg glide.PoolConfig) *glide.ClientPool {
	t.Helper()
	var cfg *config.ClientConfiguration
	if cluster {
		cfg = standaloneConfigForClusterNode()
	} else {
		cfg = standaloneConfig()
	}
	pool, err := glide.NewClientPool(cfg, poolCfg)
	require.NoError(t, err)
	return pool
}

func TestPoolCreateAndMetrics(t *testing.T) {
	for _, tc := range scopeModes() {
		t.Run(tc.name, func(t *testing.T) {
			skipMode(t, tc.cluster)
			pool := newPool(t, tc.cluster, glide.PoolConfig{
				MaxSize:        3,
				MinIdle:        2,
				AcquireTimeout: 10 * time.Second,
			})
			defer pool.Close()

			deadline := time.Now().Add(30 * time.Second)
			for pool.IdleCount() < 1 && time.Now().Before(deadline) {
				time.Sleep(500 * time.Millisecond)
			}

			assert.GreaterOrEqual(t, pool.IdleCount(), 1)
			assert.GreaterOrEqual(t, pool.TotalCount(), 1)
		})
	}
}

func TestPoolAcquireAndCommands(t *testing.T) {
	for _, tc := range scopeModes() {
		t.Run(tc.name, func(t *testing.T) {
			skipMode(t, tc.cluster)
			pool := newPool(t, tc.cluster, glide.PoolConfig{
				MaxSize:        3,
				MinIdle:        1,
				AcquireTimeout: 10 * time.Second,
			})
			defer pool.Close()

			deadline := time.Now().Add(30 * time.Second)
			for pool.IdleCount() < 1 && time.Now().Before(deadline) {
				time.Sleep(500 * time.Millisecond)
			}

			ctx := context.Background()
			clientID, err := pool.Acquire(ctx)
			require.NoError(t, err)
			assert.GreaterOrEqual(t, clientID, int64(0))

			client, err := pool.GetClient(clientID)
			require.NoError(t, err)

			key := scopeTestKey("go-pool-test", tc.cluster)
			_, err = client.Set(ctx, key, "hello")
			require.NoError(t, err)

			val, err := client.Get(ctx, key)
			require.NoError(t, err)
			assert.Equal(t, "hello", val.Value())

			client.Client.Del(ctx, []string{key})
			client.Close()
		})
	}
}

func TestPoolReuse(t *testing.T) {
	for _, tc := range scopeModes() {
		t.Run(tc.name, func(t *testing.T) {
			skipMode(t, tc.cluster)
			pool := newPool(t, tc.cluster, glide.PoolConfig{
				MaxSize:        3,
				MinIdle:        1,
				AcquireTimeout: 10 * time.Second,
			})
			defer pool.Close()

			deadline := time.Now().Add(30 * time.Second)
			for pool.IdleCount() < 1 && time.Now().Before(deadline) {
				time.Sleep(500 * time.Millisecond)
			}

			ctx := context.Background()
			id1, _ := pool.Acquire(ctx)
			pool.Release(id1)
			time.Sleep(100 * time.Millisecond)

			id2, _ := pool.Acquire(ctx)
			pool.Release(id2)

			assert.Equal(t, id1, id2)
		})
	}
}

func TestPoolExhaustionTimeout(t *testing.T) {
	for _, tc := range scopeModes() {
		t.Run(tc.name, func(t *testing.T) {
			skipMode(t, tc.cluster)
			pool := newPool(t, tc.cluster, glide.PoolConfig{
				MaxSize:        1,
				MinIdle:        1,
				AcquireTimeout: 10 * time.Second,
			})
			defer pool.Close()

			deadline := time.Now().Add(30 * time.Second)
			for pool.IdleCount() < 1 && time.Now().Before(deadline) {
				time.Sleep(500 * time.Millisecond)
			}

			ctx := context.Background()
			id1, _ := pool.Acquire(ctx)

			_, err := pool.AcquireWithTimeout(ctx, 500*time.Millisecond)
			assert.Error(t, err)
			assert.Contains(t, err.Error(), "timed out")

			pool.Release(id1)
		})
	}
}

func TestPoolConcurrentAccess(t *testing.T) {
	for _, tc := range scopeModes() {
		t.Run(tc.name, func(t *testing.T) {
			skipMode(t, tc.cluster)
			pool := newPool(t, tc.cluster, glide.PoolConfig{
				MaxSize:        4,
				MinIdle:        4,
				AcquireTimeout: 15 * time.Second,
			})
			defer pool.Close()

			deadline := time.Now().Add(30 * time.Second)
			for pool.IdleCount() < 1 && time.Now().Before(deadline) {
				time.Sleep(500 * time.Millisecond)
			}

			ctx := context.Background()
			var wg sync.WaitGroup
			errCh := make(chan error, 8)

			for i := 0; i < 8; i++ {
				wg.Add(1)
				go func(idx int) {
					defer wg.Done()
					clientID, err := pool.Acquire(ctx)
					if err != nil {
						errCh <- err
						return
					}

					client, err := pool.GetClient(clientID)
					if err != nil {
						errCh <- err
						return
					}

					key := scopeTestKey(fmt.Sprintf("pool-concurrent-%d", idx), tc.cluster)
					_, err = client.Set(ctx, key, fmt.Sprintf("val-%d", idx))
					if err != nil {
						errCh <- err
						return
					}
					client.Client.Del(ctx, []string{key})
					client.Close()
				}(i)
			}

			wg.Wait()
			close(errCh)

			for err := range errCh {
				t.Fatalf("concurrent access error: %v", err)
			}
		})
	}
}

func TestPoolCloseRejectsAcquire(t *testing.T) {
	for _, tc := range scopeModes() {
		t.Run(tc.name, func(t *testing.T) {
			skipMode(t, tc.cluster)
			pool := newPool(t, tc.cluster, glide.PoolConfig{
				MaxSize:        2,
				MinIdle:        1,
				AcquireTimeout: 10 * time.Second,
			})

			deadline := time.Now().Add(30 * time.Second)
			for pool.IdleCount() < 1 && time.Now().Before(deadline) {
				time.Sleep(500 * time.Millisecond)
			}
			pool.Close()

			ctx := context.Background()
			_, err := pool.Acquire(ctx)
			assert.Error(t, err)
			assert.Contains(t, err.Error(), "closed")
		})
	}
}

// ═══════════════════════════════════════════════════════════════════════════════
// Feature 2: IsolatedScope tests (parameterized: standalone + cluster)
// ═══════════════════════════════════════════════════════════════════════════════

func TestScopeAcquirePingRelease(t *testing.T) {
	for _, tc := range scopeModes() {
		t.Run(tc.name, func(t *testing.T) {
			skipMode(t, tc.cluster)
			client := newScopeClient(t, tc.cluster)
			defer client.Close()

			ctx := context.Background()
			scope, err := client.ScopedConnection(ctx, 10*time.Second)
			require.NoError(t, err)
			assert.False(t, scope.IsReleased())

			result, err := scope.Ping(ctx)
			require.NoError(t, err)
			assert.Equal(t, "PONG", result)

			scope.Close()
			assert.True(t, scope.IsReleased())
		})
	}
}

func TestScopeGetSet(t *testing.T) {
	for _, tc := range scopeModes() {
		t.Run(tc.name, func(t *testing.T) {
			skipMode(t, tc.cluster)
			client := newScopeClient(t, tc.cluster)
			defer client.Close()

			ctx := context.Background()
			key := scopeTestKey("go-scope-test", tc.cluster)

			scope, err := client.ScopedConnection(ctx, 10*time.Second)
			require.NoError(t, err)
			defer scope.Close()

			_, err = scope.Set(ctx, key, "hello")
			require.NoError(t, err)

			val, err := scope.Get(ctx, key)
			require.NoError(t, err)
			assert.Equal(t, "hello", val)

			client.Del(ctx, []string{key})
		})
	}
}

func TestScopeReturnsEmptyForMissingKey(t *testing.T) {
	for _, tc := range scopeModes() {
		t.Run(tc.name, func(t *testing.T) {
			skipMode(t, tc.cluster)
			client := newScopeClient(t, tc.cluster)
			defer client.Close()

			ctx := context.Background()
			scope, err := client.ScopedConnection(ctx, 10*time.Second)
			require.NoError(t, err)
			defer scope.Close()

			key := scopeTestKey("nonexistent", tc.cluster)
			val, err := scope.Get(ctx, key)
			require.NoError(t, err)
			assert.Equal(t, "", val)
		})
	}
}

func TestScopeWatchMultiExec(t *testing.T) {
	for _, tc := range scopeModes() {
		t.Run(tc.name, func(t *testing.T) {
			skipMode(t, tc.cluster)
			client := newScopeClient(t, tc.cluster)
			defer client.Close()

			ctx := context.Background()
			key := scopeTestKey("go-occ", tc.cluster)
			client.Set(ctx, key, "0")

			scope, err := client.ScopedConnection(ctx, 10*time.Second)
			require.NoError(t, err)
			defer scope.Close()

			_, err = scope.Watch(ctx, key)
			require.NoError(t, err)

			val, err := scope.Get(ctx, key)
			require.NoError(t, err)
			assert.Equal(t, "0", val)

			_, err = scope.Multi(ctx)
			require.NoError(t, err)

			_, err = scope.Set(ctx, key, "1")
			require.NoError(t, err)

			result, err := scope.Exec(ctx)
			require.NoError(t, err)
			assert.NotEmpty(t, result)

			storedVal, _ := client.Get(ctx, key)
			assert.Equal(t, "1", storedVal.Value())
			client.Del(ctx, []string{key})
		})
	}
}

func TestScopeRaisesAfterRelease(t *testing.T) {
	for _, tc := range scopeModes() {
		t.Run(tc.name, func(t *testing.T) {
			skipMode(t, tc.cluster)
			client := newScopeClient(t, tc.cluster)
			defer client.Close()

			ctx := context.Background()
			scope, err := client.ScopedConnection(ctx, 10*time.Second)
			require.NoError(t, err)

			scope.Close()

			_, err = scope.Ping(ctx)
			assert.Error(t, err)
			assert.Contains(t, err.Error(), "released")
		})
	}
}

func TestScopeWatchConflictAbortsExec(t *testing.T) {
	for _, tc := range scopeModes() {
		t.Run(tc.name, func(t *testing.T) {
			skipMode(t, tc.cluster)
			client := newScopeClient(t, tc.cluster)
			defer client.Close()

			ctx := context.Background()
			key := scopeTestKey("go-occ-conflict", tc.cluster)
			client.Set(ctx, key, "original")

			scope, err := client.ScopedConnection(ctx, 10*time.Second)
			require.NoError(t, err)
			defer scope.Close()

			_, err = scope.Watch(ctx, key)
			require.NoError(t, err)
			_, err = scope.Get(ctx, key)
			require.NoError(t, err)

			// Modify externally via the main client
			client.Set(ctx, key, "modified-externally")

			_, err = scope.Multi(ctx)
			require.NoError(t, err)
			_, err = scope.Set(ctx, key, "from-scope")
			require.NoError(t, err)

			result, err := scope.Exec(ctx)
			require.NoError(t, err)
			assert.Empty(t, result)

			val, _ := client.Get(ctx, key)
			assert.Equal(t, "modified-externally", val.Value())
			client.Del(ctx, []string{key})
		})
	}
}

func TestScopeOCCConcurrentIncrement(t *testing.T) {
	for _, tc := range scopeModes() {
		t.Run(tc.name, func(t *testing.T) {
			skipMode(t, tc.cluster)
			client := newScopeClient(t, tc.cluster)
			defer client.Close()

			ctx := context.Background()
			key := scopeTestKey("go-occ-counter", tc.cluster)
			client.Set(ctx, key, "0")

			numGoroutines := 4
			incrementsPerGoroutine := 10
			expectedFinal := numGoroutines * incrementsPerGoroutine

			var wg sync.WaitGroup
			errCh := make(chan error, numGoroutines)

			for g := 0; g < numGoroutines; g++ {
				wg.Add(1)
				go func() {
					defer wg.Done()
					for i := 0; i < incrementsPerGoroutine; i++ {
						committed := false
						for !committed {
							scope, err := client.ScopedConnection(ctx, 10*time.Second)
							if err != nil {
								errCh <- err
								return
							}
							scope.Watch(ctx, key)
							val, _ := scope.Get(ctx, key)
							current := 0
							if val != "" {
								fmt.Sscanf(val, "%d", &current)
							}
							scope.Multi(ctx)
							scope.Set(ctx, key, fmt.Sprintf("%d", current+1))
							result, _ := scope.Exec(ctx)
							scope.Close()
							if result != "" {
								committed = true
							}
						}
					}
				}()
			}

			wg.Wait()
			close(errCh)

			for err := range errCh {
				t.Fatalf("goroutine error: %v", err)
			}

			finalVal, _ := client.Get(ctx, key)
			assert.Equal(t, fmt.Sprintf("%d", expectedFinal), finalVal.Value())
			client.Del(ctx, []string{key})
		})
	}
}

func TestScopeCloseIsIdempotent(t *testing.T) {
	for _, tc := range scopeModes() {
		t.Run(tc.name, func(t *testing.T) {
			skipMode(t, tc.cluster)
			client := newScopeClient(t, tc.cluster)
			defer client.Close()

			ctx := context.Background()
			scope, err := client.ScopedConnection(ctx, 10*time.Second)
			require.NoError(t, err)

			scope.Ping(ctx)
			scope.Close()
			scope.Close() // Should not panic
			assert.True(t, scope.IsReleased())
		})
	}
}

func TestScopePoolReuse(t *testing.T) {
	for _, tc := range scopeModes() {
		t.Run(tc.name, func(t *testing.T) {
			skipMode(t, tc.cluster)
			client := newScopeClient(t, tc.cluster)
			defer client.Close()

			ctx := context.Background()

			scope1, err := client.ScopedConnection(ctx, 10*time.Second)
			require.NoError(t, err)
			scope1.Ping(ctx)
			scope1.Close()

			time.Sleep(100 * time.Millisecond)

			scope2, err := client.ScopedConnection(ctx, 10*time.Second)
			require.NoError(t, err)
			result, err := scope2.Ping(ctx)
			require.NoError(t, err)
			assert.Equal(t, "PONG", result)
			scope2.Close()
		})
	}
}

// ═══════════════════════════════════════════════════════════════════════════════
// Scope Connection Modifier Parity Tests (parameterized)
// ═══════════════════════════════════════════════════════════════════════════════

func TestScopeCompressionWritesParity(t *testing.T) {
	for _, tc := range scopeModes() {
		t.Run(tc.name, func(t *testing.T) {
			skipMode(t, tc.cluster)

			var compressedCfg, rawCfg *config.ClientConfiguration
			if tc.cluster {
				compressedCfg = compressedClusterNodeConfig()
				rawCfg = standaloneConfigForClusterNode()
			} else {
				compressedCfg = compressedStandaloneConfig()
				rawCfg = standaloneConfig()
			}

			compressedClient, err := glide.NewClient(compressedCfg)
			require.NoError(t, err)
			defer compressedClient.Close()

			rawClient, err := glide.NewClient(rawCfg)
			require.NoError(t, err)
			defer rawClient.Close()

			ctx := context.Background()
			key := scopeTestKey("go-scope-compress", tc.cluster)
			largeValue := strings.Repeat("A", 500)

			// Write via scoped connection (should compress)
			scope, err := compressedClient.ScopedConnection(ctx, 10*time.Second)
			require.NoError(t, err)
			_, err = scope.Set(ctx, key, largeValue)
			require.NoError(t, err)
			scope.Close()

			// Read with same client (decompresses) — should match
			val, err := compressedClient.Get(ctx, key)
			require.NoError(t, err)
			assert.Equal(t, largeValue, val.Value())

			// Read with raw client (no compression) — should differ
			rawVal, err := rawClient.Get(ctx, key)
			require.NoError(t, err)
			assert.NotEqual(t, largeValue, rawVal.Value(),
				"Value stored via compressed scope should be compressed in Valkey")

			compressedClient.Del(ctx, []string{key})
		})
	}
}

func TestScopeCompressionReadsParity(t *testing.T) {
	for _, tc := range scopeModes() {
		t.Run(tc.name, func(t *testing.T) {
			skipMode(t, tc.cluster)

			var compressedCfg *config.ClientConfiguration
			if tc.cluster {
				compressedCfg = compressedClusterNodeConfig()
			} else {
				compressedCfg = compressedStandaloneConfig()
			}

			compressedClient, err := glide.NewClient(compressedCfg)
			require.NoError(t, err)
			defer compressedClient.Close()

			ctx := context.Background()
			key := scopeTestKey("go-scope-read-compress", tc.cluster)
			value := strings.Repeat("CompressibleData_", 50)

			// Write via parent client (compressed)
			compressedClient.Set(ctx, key, value)

			// Read via scoped connection — should decompress correctly
			scope, err := compressedClient.ScopedConnection(ctx, 10*time.Second)
			require.NoError(t, err)
			retrieved, err := scope.Get(ctx, key)
			require.NoError(t, err)
			assert.Equal(t, value, retrieved)
			scope.Close()

			compressedClient.Del(ctx, []string{key})
		})
	}
}

func TestScopeDatabaseInheritance(t *testing.T) {
	for _, tc := range scopeModes() {
		t.Run(tc.name, func(t *testing.T) {
			skipMode(t, tc.cluster)

			if tc.cluster {
				// SELECT in cluster requires Valkey 9+
				checkClient, err := glide.NewClusterClient(clusterConfig())
				require.NoError(t, err)
				ver := getClusterServerVersion(t, checkClient)
				checkClient.Close()
				if versionLessThan(ver, "9.0.0") {
					t.Skipf("SELECT in cluster requires Valkey 9+ (got %s)", ver)
				}
			}

			// Client configured for database 2
			var cfg *config.ClientConfiguration
			if tc.cluster {
				cfg = standaloneConfigForClusterNode().WithDatabaseId(2)
			} else {
				cfg = standaloneConfig().WithDatabaseId(2)
			}

			client, err := glide.NewClient(cfg)
			require.NoError(t, err)
			defer client.Close()

			ctx := context.Background()
			key := scopeTestKey("go-scope-db2", tc.cluster)

			// Write via scope on database 2
			scope, err := client.ScopedConnection(ctx, 10*time.Second)
			require.NoError(t, err)
			_, err = scope.Set(ctx, key, "on-db2")
			require.NoError(t, err)
			val, err := scope.Get(ctx, key)
			require.NoError(t, err)
			assert.Equal(t, "on-db2", val)
			scope.Close()

			// Parent client (also on db 2) should see the key
			parentVal, err := client.Get(ctx, key)
			require.NoError(t, err)
			assert.Equal(t, "on-db2", parentVal.Value())

			// A client on database 0 should NOT see the key
			var db0Cfg *config.ClientConfiguration
			if tc.cluster {
				db0Cfg = standaloneConfigForClusterNode()
			} else {
				db0Cfg = standaloneConfig()
			}
			db0Client, err := glide.NewClient(db0Cfg)
			require.NoError(t, err)
			defer db0Client.Close()
			db0Val, err := db0Client.Get(ctx, key)
			require.NoError(t, err)
			assert.Equal(t, "", db0Val.Value(),
				"Key on db2 should not be visible on db0")

			client.Del(ctx, []string{key})
		})
	}
}

func TestScopeInflightLimit(t *testing.T) {
	for _, tc := range scopeModes() {
		t.Run(tc.name, func(t *testing.T) {
			skipMode(t, tc.cluster)
			client := newScopeClient(t, tc.cluster)
			defer client.Close()

			ctx := context.Background()
			scope, err := client.ScopedConnection(ctx, 10*time.Second)
			require.NoError(t, err)
			defer scope.Close()

			// 50 sequential SET/GET/DEL cycles should work within inflight limits
			for i := 0; i < 50; i++ {
				key := scopeTestKey(fmt.Sprintf("inflight-%d", i), tc.cluster)
				_, err := scope.Set(ctx, key, fmt.Sprintf("value-%d", i))
				require.NoError(t, err)

				val, err := scope.Get(ctx, key)
				require.NoError(t, err)
				assert.Equal(t, fmt.Sprintf("value-%d", i), val)

				_, err = scope.ExecuteCommand(ctx, "DEL", key)
				require.NoError(t, err)
			}
		})
	}
}

func TestPoolPublish(t *testing.T) {
	for _, tc := range scopeModes() {
		t.Run(tc.name, func(t *testing.T) {
			skipMode(t, tc.cluster)

			pool, err := glide.NewClientPool(
				func() *config.ClientConfiguration {
					if tc.cluster {
						return standaloneConfigForClusterNode()
					}
					return standaloneConfig()
				}(),
				glide.PoolConfig{
					MaxSize:        2,
					MinIdle:        1,
					AcquireTimeout: 10 * time.Second,
				},
			)
			require.NoError(t, err)
			defer pool.Close()

			deadline := time.Now().Add(30 * time.Second)
			for pool.IdleCount() < 1 && time.Now().Before(deadline) {
				time.Sleep(500 * time.Millisecond)
			}

			ctx := context.Background()
			clientID, err := pool.Acquire(ctx)
			require.NoError(t, err)

			client, err := pool.GetClient(clientID)
			require.NoError(t, err)

			// PUBLISH to a channel via custom command — just verify no crash
			channel := scopeTestKey("pool-pub-channel", tc.cluster)
			_, err = client.CustomCommand(ctx, []string{"PUBLISH", channel, "test-message"})
			require.NoError(t, err, "PUBLISH via pooled client should not crash")

			client.Close()
		})
	}
}

func TestScopeReleaseResetsDatabase(t *testing.T) {
	for _, tc := range scopeModes() {
		t.Run(tc.name, func(t *testing.T) {
			skipMode(t, tc.cluster)

			if tc.cluster {
				// SELECT in cluster requires Valkey 9+
				checkClient, err := glide.NewClusterClient(clusterConfig())
				require.NoError(t, err)
				ver := getClusterServerVersion(t, checkClient)
				checkClient.Close()
				if versionLessThan(ver, "9.0.0") {
					t.Skipf("SELECT in cluster requires Valkey 9+ (got %s)", ver)
				}
			}

			client := newScopeClient(t, tc.cluster)
			defer client.Close()

			ctx := context.Background()
			key := scopeTestKey("go-scope-db-reset", tc.cluster)

			// First scope: SELECT to db 4 and write
			scope1, err := client.ScopedConnection(ctx, 10*time.Second)
			require.NoError(t, err)
			_, err = scope1.ExecuteCommand(ctx, "SELECT", "4")
			require.NoError(t, err)
			_, err = scope1.Set(ctx, key, "on-db4")
			require.NoError(t, err)
			scope1.Close()

			// Allow async cleanup
			time.Sleep(300 * time.Millisecond)

			// Second scope: should be on db 0 (reset happened)
			scope2, err := client.ScopedConnection(ctx, 10*time.Second)
			require.NoError(t, err)
			val, err := scope2.Get(ctx, key)
			require.NoError(t, err)
			assert.Equal(t, "", val,
				"Scope release should reset database — second scope should be on db 0")
			scope2.Close()

			// Cleanup key on db 4
			var cleanupCfg *config.ClientConfiguration
			if tc.cluster {
				cleanupCfg = standaloneConfigForClusterNode().WithDatabaseId(4)
			} else {
				cleanupCfg = standaloneConfig().WithDatabaseId(4)
			}
			cleanupClient, _ := glide.NewClient(cleanupCfg)
			if cleanupClient != nil {
				cleanupClient.Del(ctx, []string{key})
				cleanupClient.Close()
			}
		})
	}
}

// TestPoolRejectsPubsubConfig verifies that pool creation fails fast when the
// client configuration has pubsub subscriptions.
func TestPoolRejectsPubsubConfig(t *testing.T) {
	t.Run("standalone_with_pubsub", func(t *testing.T) {
		cfg := standaloneConfig().
			WithSubscriptionConfig(
				config.NewStandaloneSubscriptionConfig().
					WithSubscription(config.ExactChannelMode, "test-channel"),
			)

		poolCfg := glide.DefaultPoolConfig()
		poolCfg.MaxSize = 2
		poolCfg.MinIdle = 1

		_, err := glide.NewClientPool(cfg, poolCfg)
		require.Error(t, err)
		assert.Contains(t, err.Error(), "pubsub")
	})

	t.Run("cluster_with_pubsub", func(t *testing.T) {
		if !clusterAvailable() {
			t.Skip("No cluster endpoints configured")
		}

		cfg := clusterConfig().
			WithSubscriptionConfig(
				config.NewClusterSubscriptionConfig().
					WithSubscription(config.ExactClusterChannelMode, "test-channel"),
			)

		poolCfg := glide.DefaultPoolConfig()
		poolCfg.MaxSize = 2
		poolCfg.MinIdle = 1

		_, err := glide.NewClusterClientPool(cfg, poolCfg)
		require.Error(t, err)
		assert.Contains(t, err.Error(), "pubsub")
	})
}
