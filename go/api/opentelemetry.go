package api

/*
#cgo LDFLAGS: -lglide_ffi
#include "../lib.h"
#include <stdlib.h>
*/
import "C"
import (
	"errors"
	"unsafe"
)

// OtelExporterConfig represents the exporter config for traces or metrics.
type OtelExporterConfig struct {
	Endpoint         string
	SamplePercentage uint32 // Only for traces
}

// OpenTelemetryConfig represents the full OTel config for initialization.
type OpenTelemetryConfig struct {
	Traces          OtelExporterConfig
	Metrics         OtelExporterConfig
	FlushIntervalMs int64
}

// InitOpenTelemetry initializes OpenTelemetry for the process. Call once before using the client.
//
// Example usage:
//
//	err := api.InitOpenTelemetry(api.OpenTelemetryConfig{
//	    Traces: api.OtelExporterConfig{
//	        Endpoint: "http://localhost:4318/v1/traces",
//	        SamplePercentage: 10,
//	    },
//	    Metrics: api.OtelExporterConfig{
//	        Endpoint: "http://localhost:4318/v1/metrics",
//	    },
//	    FlushIntervalMs: 5000,
//	})
//	if err != nil { panic(err) }
func InitOpenTelemetry(cfg OpenTelemetryConfig) error {
	tracesEndpoint := C.CString(cfg.Traces.Endpoint)
	metricsEndpoint := C.CString(cfg.Metrics.Endpoint)
	defer C.free(unsafe.Pointer(tracesEndpoint))
	defer C.free(unsafe.Pointer(metricsEndpoint))

	cTraces := C.struct_OtelExporterConfig{
		endpoint:          tracesEndpoint,
		sample_percentage: C.uint(cfg.Traces.SamplePercentage),
	}
	cMetrics := C.struct_OtelExporterConfig{
		endpoint:          metricsEndpoint,
		sample_percentage: 0, // Only used for traces
	}
	cCfg := C.struct_OpenTelemetryConfig{
		traces:            cTraces,
		metrics:           cMetrics,
		flush_interval_ms: C.longlong(cfg.FlushIntervalMs),
	}

	ret := C.init_open_telemetry(cCfg)
	if ret != 0 {
		return errors.New("failed to initialize OpenTelemetry")
	}
	return nil
}
