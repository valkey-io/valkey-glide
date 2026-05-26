// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

package integTest

import (
	"context"
	"errors"
	"runtime"
	"time"

	"github.com/google/uuid"
	glide "github.com/valkey-io/valkey-glide/go/v2"
	"github.com/valkey-io/valkey-glide/go/v2/interfaces"
)

func (suite *GlideTestSuite) TestParallelizedSetWithGC() {
	// The insane 640 parallelism is required to reproduce https://github.com/valkey-io/valkey-glide/issues/3207.
	suite.runParallelizedWithDefaultClients(640, 640000, 2*time.Minute, func(client interfaces.BaseClientCommands) {
		runtime.GC()
		key := uuid.New().String()
		value := uuid.New().String()
		// Retry on transient connection errors (e.g. "Pipeline channel full") that occur under GC pressure.
		// The client will reconnect automatically, so retrying after a brief wait should succeed.
		var result string
		var err error
		for attempt := 0; attempt < 3; attempt++ {
			result, err = client.Set(context.Background(), key, value)
			if err == nil {
				break
			}
			var discErr *glide.DisconnectError
			if !errors.As(err, &discErr) {
				break
			}
			time.Sleep(100 * time.Millisecond)
		}
		suite.verifyOK(result, err)
	})
}
