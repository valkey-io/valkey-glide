// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

package integTest

import (
	"context"
	"runtime"
	"time"

	"github.com/google/uuid"
	"github.com/valkey-io/valkey-glide/go/v2/interfaces"
)

func (suite *GlideTestSuite) TestParallelizedSetWithGC() {
	// Reduced parallelism from 640 to 64 to prevent pipeline channel overflow
	// while still testing GC interaction with concurrent operations
	suite.runParallelizedWithDefaultClients(64, 64000, 2*time.Minute, func(client interfaces.BaseClientCommands) {
		runtime.GC()
		key := uuid.New().String()
		value := uuid.New().String()
		suite.verifyOK(client.Set(context.Background(), key, value))
	})
}
