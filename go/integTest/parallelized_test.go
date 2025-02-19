package integTest

import (
	"runtime"

	"github.com/google/uuid"
	"github.com/valkey-io/valkey-glide/go/api"
)

func (suite *GlideTestSuite) TestParallelizedSetWithGC() {
	// The insane 640 parallelism is required to reproduce https://github.com/valkey-io/valkey-glide/issues/3207.
	suite.runParallelizedWithDefaultClients(640, 640000, func(client api.BaseClient) {
		runtime.GC()
		key := uuid.New().String()
		value := uuid.New().String()
		suite.verifyOK(client.Set(key, value))
	})
}
