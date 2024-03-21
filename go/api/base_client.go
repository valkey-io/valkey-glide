// Copyright GLIDE-for-Redis Project Contributors - SPDX Identifier: Apache-2.0

package api

// #cgo LDFLAGS: -L../target/release -lglide_rs
// #include "../lib.h"
//
// void successCallback(uintptr_t channelPtr, char *message);
// void failureCallback(uintptr_t channelPtr, char *errMessage, RequestErrorType errType);
import "C"

import (
    "unsafe"

    "github.com/aws/glide-for-redis/go/glide/protobuf"
    "google.golang.org/protobuf/proto"
)

//export successCallback
func successCallback(channelPtr C.uintptr_t, cResponse *C.char) {
    // TODO: Implement when we implement the command logic
}

//export failureCallback
func failureCallback(channelPtr C.uintptr_t, cErrorMessage *C.char, cErrorType C.RequestErrorType) {
    // TODO: Implement when we implement the command logic
}

type clientConfiguration interface {
    toProtobuf() *protobuf.ConnectionRequest
}

type baseClient struct {
    coreClient unsafe.Pointer
}

func createClient(config clientConfiguration) (*baseClient, error) {
    request := config.toProtobuf()
    msg, err := proto.Marshal(request)
    if err != nil {
        return nil, err
    }

    byteCount := len(msg)
    requestBytes := C.CBytes(msg)
    cResponse := (*C.struct_ConnectionResponse)(
        C.create_client(
            (*C.uchar)(requestBytes),
            C.uintptr_t(byteCount),
            (C.SuccessCallback)(unsafe.Pointer(C.successCallback)),
            (C.FailureCallback)(unsafe.Pointer(C.failureCallback)),
        ),
    )
    defer C.free_connection_response(cResponse)

    cErr := cResponse.connection_error_message
    if cErr != nil {
        message := C.GoString(cErr)
        return nil, &ConnectionError{message}
    }

    return &baseClient{cResponse.conn_ptr}, nil
}

// Close terminates the client by closing all associated resources.
func (client *baseClient) Close() {
    if client.coreClient == nil {
        return
    }

    C.close_client(client.coreClient)
    client.coreClient = nil
}
