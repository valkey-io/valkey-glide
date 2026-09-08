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
	// The insane 640 parallelism is required to reproduce https://github.com/valkey-io/valkey-glide/issues/3207.
	// Use a longer timeout to accommodate AddressSanitizer overhead in container environments
	// where GC pressure from 640 goroutines significantly increases execution time.
	suite.runParallelizedWithDefaultClients(640, 640000, 5*time.Minute, func(client interfaces.BaseClientCommands) {
		runtime.GC()
		key := uuid.New().String()
		value := uuid.New().String()
		suite.verifyOK(client.Set(context.Background(), key, value))
	})
}
