// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

package integTest

import (
	"context"
	"fmt"
	"runtime"
	"time"

	"github.com/google/uuid"
	"github.com/valkey-io/valkey-glide/go/v2/interfaces"
)

func (suite *GlideTestSuite) TestParallelizedSetWithGC() {
	// The insane 640 parallelism is required to reproduce https://github.com/valkey-io/valkey-glide/issues/3207.
	suite.runParallelizedWithDefaultClients(640, 640000, 2*time.Minute, func(client interfaces.BaseClientCommands) {
		runtime.GC()
		
		// Use unique values to detect channel correlation issues
		key := uuid.New().String()
		value := fmt.Sprintf("val_%d_%s", time.Now().UnixNano(), uuid.New().String()[:8])
		
		// Fail fast - no retries that mask channel delivery problems  
		result, err := client.Set(context.Background(), key, value)
		suite.verifyOK(result, err)
		
		// Verify channel correlation by checking we get back exactly what we set
		retrieved, err := client.Get(context.Background(), key)
		suite.Require().NoError(err)
		suite.Require().Equal(value, retrieved.Value())
	})
}