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

// firstEndpoint returns the first address in an endpoints flag, or the given fallback when the flag is
// empty. skipMode skips every pool and scope test when the flag is empty, so the fallback just records
// the conventional port.
func firstEndpoint(endpoints *string, fallbackHost string, fallbackPort int) config.NodeAddress {
	host, port := fallbackHost, fallbackPort
	if endpoints != nil && *endpoints != "" {
		parts := strings.SplitN(*endpoints, ",", 2)
		hostPort := strings.SplitN(parts[0], ":", 2)
		if len(hostPort) == 2 {
			host = hostPort[0]
			if p, err := strconv.Atoi(hostPort[1]); err == nil {
				port = p
			}
		}
	}
	return config.NodeAddress{Host: host, Port: port}
}

func standaloneConfig() *config.ClientConfiguration {
	return clientConfigFor(firstEndpoint(standaloneHosts, "localhost", 6379))
}

// standaloneConfigForClusterNode returns a standalone ClientConfiguration
// pointed at the first cluster node. This allows using ScopedConnection against
// a cluster node for single-slot operations with hash-tagged keys.
func standaloneConfigForClusterNode() *config.ClientConfiguration {
	return clientConfigFor(firstEndpoint(clusterHosts, "localhost", 7000))
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
	return clusterClientConfigFor(firstEndpoint(clusterHosts, "localhost", 7000))
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
	ScopedConnection(ctx context.Context, timeout time.Duration, routingKey string) (*glide.IsolatedScope, error)
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
// ClientPool tests (parameterized: standalone + cluster)
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

// waitForPoolReady polls until the pool has at least minIdle idle clients.
func waitForPoolReady(t *testing.T, pool *glide.ClientPool, minIdle int) {
	t.Helper()
	deadline := time.Now().Add(30 * time.Second)
	for pool.IdleCount() < minIdle && time.Now().Before(deadline) {
		time.Sleep(50 * time.Millisecond)
	}
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

			waitForPoolReady(t, pool, 1)

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

			waitForPoolReady(t, pool, 1)

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

			waitForPoolReady(t, pool, 1)

			ctx := context.Background()
			id1, _ := pool.Acquire(ctx)
			pool.Release(id1)
			time.Sleep(50 * time.Millisecond)

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

			waitForPoolReady(t, pool, 1)

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

			waitForPoolReady(t, pool, 1)

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

			waitForPoolReady(t, pool, 1)
			pool.Close()

			ctx := context.Background()
			_, err := pool.Acquire(ctx)
			assert.Error(t, err)
			assert.Contains(t, err.Error(), "closed")
		})
	}
}

// ═══════════════════════════════════════════════════════════════════════════════
// IsolatedScope tests (parameterized: standalone + cluster)
// ═══════════════════════════════════════════════════════════════════════════════

func TestScopeAcquirePingRelease(t *testing.T) {
	for _, tc := range scopeModes() {
		t.Run(tc.name, func(t *testing.T) {
			skipMode(t, tc.cluster)
			client := newScopeClient(t, tc.cluster)
			defer client.Close()

			ctx := context.Background()
			scope, err := client.ScopedConnection(ctx, 10*time.Second, "")
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

			scope, err := client.ScopedConnection(ctx, 10*time.Second, "")
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
			scope, err := client.ScopedConnection(ctx, 10*time.Second, "")
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

			scope, err := client.ScopedConnection(ctx, 10*time.Second, "")
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
			scope, err := client.ScopedConnection(ctx, 10*time.Second, "")
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

			scope, err := client.ScopedConnection(ctx, 10*time.Second, "")
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
							scope, err := client.ScopedConnection(ctx, 10*time.Second, "")
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
			scope, err := client.ScopedConnection(ctx, 10*time.Second, "")
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

			scope1, err := client.ScopedConnection(ctx, 10*time.Second, "")
			require.NoError(t, err)
			scope1.Ping(ctx)
			scope1.Close()

			time.Sleep(50 * time.Millisecond)

			scope2, err := client.ScopedConnection(ctx, 10*time.Second, "")
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
			scope, err := compressedClient.ScopedConnection(ctx, 10*time.Second, "")
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
			scope, err := compressedClient.ScopedConnection(ctx, 10*time.Second, "")
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
			scope, err := client.ScopedConnection(ctx, 10*time.Second, "")
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
			scope, err := client.ScopedConnection(ctx, 10*time.Second, "")
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

			waitForPoolReady(t, pool, 1)

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
			scope1, err := client.ScopedConnection(ctx, 10*time.Second, "")
			require.NoError(t, err)
			_, err = scope1.ExecuteCommand(ctx, "SELECT", "4")
			require.NoError(t, err)
			_, err = scope1.Set(ctx, key, "on-db4")
			require.NoError(t, err)
			scope1.Close()

			// Allow async cleanup
			time.Sleep(50 * time.Millisecond)

			// Second scope: should be on db 0 (reset happened)
			scope2, err := client.ScopedConnection(ctx, 10*time.Second, "")
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

// ═══════════════════════════════════════════════════════════════════════════════
// Additional Pool & Scope tests
// ═══════════════════════════════════════════════════════════════════════════════

func TestPoolPublishConcurrent(t *testing.T) {
	for _, tc := range scopeModes() {
		t.Run(tc.name, func(t *testing.T) {
			skipMode(t, tc.cluster)

			pool := newPool(t, tc.cluster, glide.PoolConfig{
				MaxSize:        4,
				MinIdle:        4,
				AcquireTimeout: 10 * time.Second,
			})
			defer pool.Close()

			waitForPoolReady(t, pool, 1)

			ctx := context.Background()
			const numGoroutines = 4
			const messagesPerGoroutine = 5

			var wg sync.WaitGroup
			errCh := make(chan error, numGoroutines*messagesPerGoroutine)

			for g := 0; g < numGoroutines; g++ {
				wg.Add(1)
				go func(gIdx int) {
					defer wg.Done()
					clientID, err := pool.Acquire(ctx)
					if err != nil {
						errCh <- fmt.Errorf("goroutine %d acquire: %w", gIdx, err)
						return
					}
					client, err := pool.GetClient(clientID)
					if err != nil {
						errCh <- fmt.Errorf("goroutine %d get client: %w", gIdx, err)
						return
					}
					defer client.Close()

					channel := scopeTestKey(fmt.Sprintf("pool-pub-concurrent-%d", gIdx), tc.cluster)
					for m := 0; m < messagesPerGoroutine; m++ {
						msg := fmt.Sprintf("msg-%d-%d", gIdx, m)
						_, err := client.CustomCommand(ctx, []string{"PUBLISH", channel, msg})
						if err != nil {
							errCh <- fmt.Errorf("goroutine %d publish %d: %w", gIdx, m, err)
							return
						}
					}
				}(g)
			}

			wg.Wait()
			close(errCh)

			for err := range errCh {
				t.Fatalf("concurrent publish error: %v", err)
			}
		})
	}
}

func TestPoolBlockingCmdIsolation(t *testing.T) {
	// Proves that a blocking command on one pool client does not block another.
	t.Run("standalone", func(t *testing.T) {
		if standaloneHosts == nil || *standaloneHosts == "" {
			t.Skip("No --standalone-endpoints provided")
		}

		pool := newPool(t, false, glide.PoolConfig{
			MaxSize:        2,
			MinIdle:        2,
			AcquireTimeout: 10 * time.Second,
			AbandonTimeout: -1 * time.Second, // disable — test intentionally holds a blocking client
		})
		defer pool.Close()

		waitForPoolReady(t, pool, 2)

		ctx := context.Background()

		// Acquire two separate pool clients
		id1, err := pool.Acquire(ctx)
		require.NoError(t, err)
		id2, err := pool.Acquire(ctx)
		require.NoError(t, err)

		client1, err := pool.GetClient(id1)
		require.NoError(t, err)
		client2, err := pool.GetClient(id2)
		require.NoError(t, err)

		blpopKey := fmt.Sprintf("pool-blpop-iso-%d", time.Now().UnixNano())
		setKey := fmt.Sprintf("pool-getset-iso-%d", time.Now().UnixNano())

		// Client1: run BLPOP with short timeout (will time out on server after 1s)
		var blpopDone sync.WaitGroup
		blpopDone.Add(1)
		go func() {
			defer blpopDone.Done()
			client1.CustomCommand(ctx, []string{"BLPOP", blpopKey, "1"})
		}()

		// Client2: GET/SET should complete quickly despite client1 blocking
		time.Sleep(100 * time.Millisecond) // let BLPOP dispatch first
		start := time.Now()
		_, err = client2.Set(ctx, setKey, "fast-value")
		require.NoError(t, err)

		val, err := client2.Get(ctx, setKey)
		require.NoError(t, err)
		assert.Equal(t, "fast-value", val.Value())
		elapsed := time.Since(start)

		assert.Less(t, elapsed, 1*time.Second,
			"GET/SET on client2 should complete in <1s while client1 BLPOP is blocking")

		// Wait for BLPOP to time out (1s server-side timeout)
		done := make(chan struct{})
		go func() {
			blpopDone.Wait()
			close(done)
		}()
		select {
		case <-done:
			// OK — BLPOP timed out as expected
		case <-time.After(10 * time.Second):
			t.Fatal("BLPOP goroutine did not complete within 10s (expected 1s server timeout)")
		}

		// Cleanup
		client2.Client.Del(ctx, []string{setKey})
		pool.Release(id1)
		pool.Release(id2)
	})
}

func TestScopeConnectionReuse(t *testing.T) {
	for _, tc := range scopeModes() {
		t.Run(tc.name, func(t *testing.T) {
			skipMode(t, tc.cluster)
			client := newScopeClient(t, tc.cluster)
			defer client.Close()

			ctx := context.Background()

			// Acquire, PING, release × 3 with 100ms gaps.
			// Verifies the scope pool reuses connections without error.
			for i := 0; i < 3; i++ {
				scope, err := client.ScopedConnection(ctx, 10*time.Second, "")
				require.NoError(t, err, "iteration %d: acquire", i)

				result, err := scope.Ping(ctx)
				require.NoError(t, err, "iteration %d: ping", i)
				assert.Equal(t, "PONG", result, "iteration %d: pong", i)

				scope.Close()
				time.Sleep(50 * time.Millisecond)
			}
		})
	}
}

func TestScopeConcurrentMultiple(t *testing.T) {
	for _, tc := range scopeModes() {
		t.Run(tc.name, func(t *testing.T) {
			skipMode(t, tc.cluster)
			client := newScopeClient(t, tc.cluster)
			defer client.Close()

			ctx := context.Background()
			keyA := scopeTestKey("scope-concurrent-a", tc.cluster)
			keyB := scopeTestKey("scope-concurrent-b", tc.cluster)

			// Seed both keys
			client.Set(ctx, keyA, "initial-a")
			client.Set(ctx, keyB, "initial-b")

			// Scope1: WATCH keyA, read, then wait for scope2 to modify keyA
			// Scope2: WATCH keyB, modify keyA externally, then commit on keyB
			scope1, err := client.ScopedConnection(ctx, 10*time.Second, "")
			require.NoError(t, err)
			defer scope1.Close()

			scope2, err := client.ScopedConnection(ctx, 10*time.Second, "")
			require.NoError(t, err)
			defer scope2.Close()

			// Scope1 watches keyA
			_, err = scope1.Watch(ctx, keyA)
			require.NoError(t, err)
			val1, err := scope1.Get(ctx, keyA)
			require.NoError(t, err)
			assert.Equal(t, "initial-a", val1)

			// Scope2 watches keyB and then modifies keyA (conflict for scope1)
			_, err = scope2.Watch(ctx, keyB)
			require.NoError(t, err)
			_, err = scope2.Set(ctx, keyA, "modified-by-scope2")
			require.NoError(t, err)

			// Scope1 tries to commit a transaction on keyA — should be aborted
			_, err = scope1.Multi(ctx)
			require.NoError(t, err)
			_, err = scope1.Set(ctx, keyA, "from-scope1")
			require.NoError(t, err)
			result1, err := scope1.Exec(ctx)
			require.NoError(t, err)
			assert.Empty(t, result1, "scope1 EXEC should return empty (aborted) due to WATCH conflict")

			// Scope2 commits on keyB — should succeed (no conflict on keyB)
			_, err = scope2.Multi(ctx)
			require.NoError(t, err)
			_, err = scope2.Set(ctx, keyB, "from-scope2")
			require.NoError(t, err)
			result2, err := scope2.Exec(ctx)
			require.NoError(t, err)
			assert.NotEmpty(t, result2, "scope2 EXEC should succeed — keyB was not modified externally")

			// Verify final state
			finalA, _ := client.Get(ctx, keyA)
			assert.Equal(t, "modified-by-scope2", finalA.Value(),
				"keyA should retain scope2's modification since scope1 was aborted")

			finalB, _ := client.Get(ctx, keyB)
			assert.Equal(t, "from-scope2", finalB.Value(),
				"keyB should have scope2's transaction value")

			// Cleanup
			client.Del(ctx, []string{keyA, keyB})
		})
	}
}

// ═══════════════════════════════════════════════════════════════════════════════
// Pool + Scope combination test (regression for #6763)
// ═══════════════════════════════════════════════════════════════════════════════

// TestPoolBorrowedClientScopedConnection verifies that a pool-borrowed client can open a
// ScopedConnection and execute a full WATCH/GET/MULTI/SET/EXEC cycle. This is the regression
// test for #6763: GetClient did not propagate clientConfig/connReqBytes, causing
// ScopedConnection to fail with "client configuration not available for scoped connections".
//
// Standalone-only: cluster-aware pool wrappers (GetClient returning *ClusterClient) are not
// yet supported — tracked as a separate enhancement.
func TestPoolBorrowedClientScopedConnection(t *testing.T) {
	if standaloneHosts == nil || *standaloneHosts == "" {
		t.Skip("No --standalone-endpoints provided")
	}

	pool := newPool(t, false, glide.PoolConfig{
		MaxSize:        2,
		MinIdle:        1,
		AcquireTimeout: 10 * time.Second,
	})
	defer pool.Close()

	waitForPoolReady(t, pool, 1)

	ctx := context.Background()
	clientID, err := pool.Acquire(ctx)
	require.NoError(t, err)

	client, err := pool.GetClient(clientID)
	require.NoError(t, err)
	defer client.Close()

	key := scopeTestKey("pool-scope-combo", false)

	// Seed a counter via the pooled client
	_, err = client.Set(ctx, key, "10")
	require.NoError(t, err)

	// Open a scoped connection on the pool-borrowed client
	scope, err := client.ScopedConnection(ctx, 10*time.Second, "")
	require.NoError(t, err, "ScopedConnection on pool-borrowed client should succeed (fix for #6763)")
	defer scope.Close()

	assert.False(t, scope.IsReleased())

	// Full OCC cycle: WATCH → GET → MULTI → SET → EXEC
	_, err = scope.Watch(ctx, key)
	require.NoError(t, err)

	val, err := scope.Get(ctx, key)
	require.NoError(t, err)
	assert.Equal(t, "10", val)

	_, err = scope.Multi(ctx)
	require.NoError(t, err)

	_, err = scope.Set(ctx, key, "11")
	require.NoError(t, err)

	result, err := scope.Exec(ctx)
	require.NoError(t, err)
	assert.NotEmpty(t, result, "EXEC should commit (no conflict)")

	// Verify via the pooled client
	finalVal, err := client.Get(ctx, key)
	require.NoError(t, err)
	assert.Equal(t, "11", finalVal.Value())

	// Cleanup
	client.Client.Del(ctx, []string{key})
}
