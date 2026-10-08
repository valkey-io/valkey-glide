// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

package glide

import (
	"context"
	"strings"
	"sync"
	"testing"
	"time"
	"unsafe"
)

type poolNativeRecorder struct {
	mu           sync.Mutex
	pointers     map[int64]unsafe.Pointer
	retains      map[unsafe.Pointer]int
	releases     map[unsafe.Pointer]int
	destroys     int
	directCloses int
	released     chan struct{}
}

func installPoolNativeRecorder(t *testing.T, pointers map[int64]unsafe.Pointer) *poolNativeRecorder {
	t.Helper()

	recorder := &poolNativeRecorder{
		pointers: pointers,
		retains:  make(map[unsafe.Pointer]int),
		releases: make(map[unsafe.Pointer]int),
		released: make(chan struct{}, 1),
	}

	oldGetPoolClientPointer := getPoolClientPointer
	oldRetainPoolClientAdapter := retainPoolClientAdapter
	oldReleasePoolClientAdapter := releasePoolClientAdapter
	oldDestroyClientPool := destroyClientPool
	oldCloseClientAdapter := closeClientAdapter

	getPoolClientPointer = func(clientID int64) unsafe.Pointer {
		return recorder.pointers[clientID]
	}
	retainPoolClientAdapter = func(client unsafe.Pointer) bool {
		recorder.mu.Lock()
		defer recorder.mu.Unlock()
		recorder.retains[client]++
		return true
	}
	releasePoolClientAdapter = func(client unsafe.Pointer) {
		recorder.mu.Lock()
		recorder.releases[client]++
		recorder.mu.Unlock()
		select {
		case recorder.released <- struct{}{}:
		default:
		}
	}
	destroyClientPool = func(_ int64) {
		recorder.mu.Lock()
		recorder.destroys++
		recorder.mu.Unlock()
	}
	closeClientAdapter = func(_ unsafe.Pointer) {
		recorder.mu.Lock()
		recorder.directCloses++
		recorder.mu.Unlock()
	}

	t.Cleanup(func() {
		getPoolClientPointer = oldGetPoolClientPointer
		retainPoolClientAdapter = oldRetainPoolClientAdapter
		releasePoolClientAdapter = oldReleasePoolClientAdapter
		destroyClientPool = oldDestroyClientPool
		closeClientAdapter = oldCloseClientAdapter
	})
	return recorder
}

func (r *poolNativeRecorder) counts(client unsafe.Pointer) (retains int, releases int) {
	r.mu.Lock()
	defer r.mu.Unlock()
	return r.retains[client], r.releases[client]
}

func (r *poolNativeRecorder) closeCounts() (destroys int, directCloses int) {
	r.mu.Lock()
	defer r.mu.Unlock()
	return r.destroys, r.directCloses
}

func newLeaseTestPool() *ClientPool {
	return &ClientPool{
		poolID:      42,
		pooledCache: make(map[int64]*PooledClient),
	}
}

func TestClientPoolGetClientRetainsOnlyCachedWrapperLease(t *testing.T) {
	adapter := unsafe.Pointer(new(byte))
	recorder := installPoolNativeRecorder(t, map[int64]unsafe.Pointer{7: adapter})
	pool := newLeaseTestPool()

	first, err := pool.GetClient(7)
	if err != nil {
		t.Fatalf("first GetClient failed: %v", err)
	}
	second, err := pool.GetClient(7)
	if err != nil {
		t.Fatalf("second GetClient failed: %v", err)
	}
	if first != second {
		t.Fatal("repeated GetClient returned a different pooled wrapper")
	}
	retains, releases := recorder.counts(adapter)
	if retains != 1 || releases != 0 {
		t.Fatalf("cached wrapper lease counts = retain %d, release %d; want 1, 0", retains, releases)
	}

	pool.Close()
}

func TestClientPoolCloseInvalidatesAndReleasesEveryCachedLeaseOnce(t *testing.T) {
	firstAdapter := unsafe.Pointer(new(byte))
	secondAdapter := unsafe.Pointer(new(byte))
	recorder := installPoolNativeRecorder(t, map[int64]unsafe.Pointer{
		1: firstAdapter,
		2: secondAdapter,
	})
	pool := newLeaseTestPool()

	first, err := pool.GetClient(1)
	if err != nil {
		t.Fatalf("GetClient(1) failed: %v", err)
	}
	second, err := pool.GetClient(2)
	if err != nil {
		t.Fatalf("GetClient(2) failed: %v", err)
	}

	pool.Close()
	pool.Close()

	for name, cached := range map[string]*PooledClient{"first": first, "second": second} {
		if cached.coreClient != nil {
			t.Errorf("%s cached wrapper still has a native adapter after pool close", name)
		}
	}
	for name, adapter := range map[string]unsafe.Pointer{"first": firstAdapter, "second": secondAdapter} {
		retains, releases := recorder.counts(adapter)
		if retains != 1 || releases != 1 {
			t.Errorf("%s adapter lease counts = retain %d, release %d; want 1, 1", name, retains, releases)
		}
	}
	destroys, directCloses := recorder.closeCounts()
	if destroys != 1 {
		t.Errorf("native pool destroy count = %d; want 1", destroys)
	}
	if directCloses != 0 {
		t.Errorf("pool-owned adapters passed to direct close_client %d times; want 0", directCloses)
	}
}

func TestPooledClientCommandAfterPoolCloseDoesNotDispatch(t *testing.T) {
	adapter := unsafe.Pointer(new(byte))
	installPoolNativeRecorder(t, map[int64]unsafe.Pointer{1: adapter})
	pool := newLeaseTestPool()
	client, err := pool.GetClient(1)
	if err != nil {
		t.Fatalf("GetClient failed: %v", err)
	}
	pool.Close()

	oldDispatchCommand := dispatchCommand
	dispatches := 0
	dispatchCommand = func(
		_ unsafe.Pointer,
		_ uintptr,
		_ uint32,
		_ int,
		_ unsafe.Pointer,
		_ unsafe.Pointer,
		_ unsafe.Pointer,
		_ uintptr,
		_ uint64,
	) {
		dispatches++
	}
	t.Cleanup(func() { dispatchCommand = oldDispatchCommand })

	_, err = client.Ping(context.Background())
	if err == nil || !strings.Contains(err.Error(), "client is closed") {
		t.Fatalf("Ping error = %v; want a closed-client error", err)
	}
	if dispatches != 0 {
		t.Fatalf("stale pooled wrapper dispatched %d native commands; want 0", dispatches)
	}
}

func TestClientPoolCloseWaitsForActiveDispatchBeforeReleasingLease(t *testing.T) {
	adapter := unsafe.Pointer(new(byte))
	recorder := installPoolNativeRecorder(t, map[int64]unsafe.Pointer{1: adapter})
	pool := newLeaseTestPool()
	client, err := pool.GetClient(1)
	if err != nil {
		t.Fatalf("GetClient failed: %v", err)
	}

	oldDispatchCommand := dispatchCommand
	dispatchEntered := make(chan struct{})
	allowDispatchReturn := make(chan struct{})
	dispatchCommand = func(
		gotAdapter unsafe.Pointer,
		_ uintptr,
		_ uint32,
		_ int,
		_ unsafe.Pointer,
		_ unsafe.Pointer,
		_ unsafe.Pointer,
		_ uintptr,
		_ uint64,
	) {
		if gotAdapter != adapter {
			t.Errorf("dispatch adapter = %p; want %p", gotAdapter, adapter)
		}
		close(dispatchEntered)
		<-allowDispatchReturn
	}
	t.Cleanup(func() { dispatchCommand = oldDispatchCommand })

	commandDone := make(chan error, 1)
	go func() {
		_, commandErr := client.Ping(context.Background())
		commandDone <- commandErr
	}()

	select {
	case <-dispatchEntered:
	case <-time.After(time.Second):
		t.Fatal("command did not enter native dispatch")
	}

	closeStarted := make(chan struct{})
	closeDone := make(chan struct{})
	go func() {
		close(closeStarted)
		pool.Close()
		close(closeDone)
	}()
	<-closeStarted

	deadline := time.Now().Add(time.Second)
	for pool.mu.TryLock() {
		pool.mu.Unlock()
		if time.Now().After(deadline) {
			t.Fatal("pool close did not enter its critical section")
		}
		time.Sleep(time.Millisecond)
	}
	select {
	case <-recorder.released:
		t.Fatal("pool released the adapter lease while native dispatch was using it")
	default:
	}

	close(allowDispatchReturn)
	select {
	case <-closeDone:
	case <-time.After(time.Second):
		t.Fatal("pool close hung after native dispatch returned")
	}
	select {
	case commandErr := <-commandDone:
		if commandErr == nil || !strings.Contains(commandErr.Error(), "pool is closed") {
			t.Fatalf("active command error = %v; want a pool-closed error", commandErr)
		}
	case <-time.After(time.Second):
		t.Fatal("active command hung after pool close")
	}

	retains, releases := recorder.counts(adapter)
	if retains != 1 || releases != 1 {
		t.Fatalf("adapter lease counts = retain %d, release %d; want 1, 1", retains, releases)
	}
}
