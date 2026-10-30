// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

package integTest

import (
	"bufio"
	"context"
	"net"
	"os"
	"os/exec"
	"path/filepath"
	"runtime"
	"strings"
	"sync"
	"time"

	"github.com/stretchr/testify/require"
	glide "github.com/valkey-io/valkey-glide/go/v2"
	"github.com/valkey-io/valkey-glide/go/v2/config"
)

// unixSocketServer listens only on a Unix domain socket (`--port 0`), so a connected client must be using it.
type unixSocketServer struct {
	socketPath string
	logPath    string
	cmd        *exec.Cmd
	exited     chan error
}

func startUnixSocketServer(suite *GlideTestSuite) *unixSocketServer {
	if runtime.GOOS == "windows" {
		suite.T().Skip("Unix domain sockets are not supported on Windows")
	}
	binary, err := exec.LookPath("valkey-server")
	if err != nil {
		binary, err = exec.LookPath("redis-server")
	}
	if err != nil {
		if os.Getenv("CI") != "" {
			suite.T().Fatal("valkey-server/redis-server is not installed")
		}
		suite.T().Skip("valkey-server/redis-server is not installed locally")
	}

	// Unix socket paths are limited to ~104-108 bytes, so avoid long TMPDIRs.
	dir, err := os.MkdirTemp("/tmp", "glide-uds-")
	require.NoError(suite.T(), err)
	suite.T().Cleanup(func() { _ = os.RemoveAll(dir) })
	server := &unixSocketServer{
		socketPath: filepath.Join(dir, "valkey.sock"),
		logPath:    filepath.Join(dir, "server.log"),
		exited:     make(chan error, 1),
	}
	server.cmd = exec.Command(
		binary,
		"--port", "0",
		"--unixsocket", server.socketPath,
		"--unixsocketperm", "700",
		"--dir", dir,
		"--logfile", server.logPath,
		"--save", "",
		"--appendonly", "no",
	)
	require.NoError(suite.T(), server.cmd.Start())
	go func() { server.exited <- server.cmd.Wait() }()
	suite.T().Cleanup(server.stop)

	deadline := time.Now().Add(10 * time.Second)
	for time.Now().Before(deadline) {
		select {
		case err := <-server.exited:
			server.exited <- err
			log, _ := os.ReadFile(server.logPath)
			suite.T().Fatalf("server exited early (%v):\n%s", err, log)
		default:
		}
		if server.ping() {
			return server
		}
		time.Sleep(50 * time.Millisecond)
	}
	log, _ := os.ReadFile(server.logPath)
	suite.T().Fatalf("server did not listen on %s in time:\n%s", server.socketPath, log)
	return nil
}

func (server *unixSocketServer) ping() bool {
	conn, err := net.DialTimeout("unix", server.socketPath, time.Second)
	if err != nil {
		return false
	}
	defer conn.Close()
	_ = conn.SetDeadline(time.Now().Add(time.Second))
	if _, err := conn.Write([]byte("PING\r\n")); err != nil {
		return false
	}
	reply, err := bufio.NewReader(conn).ReadString('\n')
	return err == nil && strings.HasPrefix(reply, "+PONG")
}

func (server *unixSocketServer) stop() {
	_ = server.cmd.Process.Kill()
	<-server.exited
}

func (server *unixSocketServer) clientConfig() *config.ClientConfiguration {
	return plaintextClientConfigFor(config.NodeAddress{UnixSocketPath: server.socketPath})
}

// assertUsesUnixSocket checks CLIENT INFO for the `U` (Unix socket) flag and returns its fields.
func assertUsesUnixSocket(suite *GlideTestSuite, client *glide.Client) map[string]string {
	info, err := client.CustomCommand(context.Background(), []string{"CLIENT", "INFO"})
	require.NoError(suite.T(), err)
	entries := parseClientListEntries(info.(string))
	require.Len(suite.T(), entries, 1, info)
	for _, fields := range entries {
		require.Contains(suite.T(), fields["flags"], "U", "expected a Unix socket client, got: %s", info)
		return fields
	}
	return nil
}

func (suite *GlideTestSuite) TestUnixSocket_ConnectSetGet() {
	server := startUnixSocketServer(suite)
	client, err := glide.NewClient(server.clientConfig().WithDatabaseId(2))
	require.NoError(suite.T(), err)
	defer client.Close()

	ctx := context.Background()
	_, err = client.Set(ctx, "uds-key", "uds-value")
	require.NoError(suite.T(), err)
	value, err := client.Get(ctx, "uds-key")
	require.NoError(suite.T(), err)
	suite.Equal("uds-value", value.Value())
	suite.Equal("2", assertUsesUnixSocket(suite, client)["db"])
}

func (suite *GlideTestSuite) TestUnixSocket_ClientPool() {
	server := startUnixSocketServer(suite)
	ctx := context.Background()
	pool, err := glide.NewClientPool(server.clientConfig(), glide.DefaultPoolConfig())
	require.NoError(suite.T(), err)
	defer pool.Close()
	clientID, err := pool.Acquire(ctx)
	require.NoError(suite.T(), err)
	pooled, err := pool.GetClient(clientID)
	require.NoError(suite.T(), err)
	_, err = pooled.Set(ctx, "uds-pool-key", "pooled")
	require.NoError(suite.T(), err)
	assertUsesUnixSocket(suite, pooled.Client)
	pool.Release(clientID)
}

func (suite *GlideTestSuite) TestUnixSocket_ScopedConnection() {
	server := startUnixSocketServer(suite)
	ctx := context.Background()
	client, err := glide.NewClient(server.clientConfig())
	require.NoError(suite.T(), err)
	defer client.Close()
	scope, err := client.ScopedConnection(ctx, 5*time.Second, "")
	require.NoError(suite.T(), err)
	defer scope.Close()
	_, err = scope.Set(ctx, "uds-scope-key", "scoped")
	require.NoError(suite.T(), err)
	value, err := scope.Get(ctx, "uds-scope-key")
	require.NoError(suite.T(), err)
	suite.Equal("scoped", value)
}

func (suite *GlideTestSuite) TestUnixSocket_Monitor() {
	server := startUnixSocketServer(suite)
	var mu sync.Mutex
	var received []glide.MonitorLine
	monitor, err := glide.NewMonitorClient(server.clientConfig(), func(line glide.MonitorLine) {
		mu.Lock()
		defer mu.Unlock()
		received = append(received, line)
	})
	require.NoError(suite.T(), err)
	defer monitor.Close()

	client, err := glide.NewClient(server.clientConfig())
	require.NoError(suite.T(), err)
	defer client.Close()
	_, err = client.Set(context.Background(), "uds-monitor-key", "value")
	require.NoError(suite.T(), err)

	expectedAddr := "unix:" + server.socketPath
	require.Eventually(suite.T(), func() bool {
		mu.Lock()
		defer mu.Unlock()
		for _, line := range received {
			if strings.EqualFold(line.Command, "SET") && line.ClientAddr == expectedAddr {
				return true
			}
		}
		return false
	}, 15*time.Second, 10*time.Millisecond, "SET over the socket not seen by MONITOR")
}
