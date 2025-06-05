// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

package internal

// #include "../lib.h"
import "C"

import (
	"fmt"
	"reflect"
	"sort"
	"strconv"
	"time"

	"github.com/valkey-io/valkey-glide/go/v2/config"
	"github.com/valkey-io/valkey-glide/go/v2/internal/errors"
	"github.com/valkey-io/valkey-glide/go/v2/models"
	"github.com/valkey-io/valkey-glide/go/v2/options"
)

type Batch struct {
	Commands []Cmd
	IsAtomic bool
	Errors   []string // errors processing command args, spotted while batch is filled
}

type Cmd struct {
	RequestType uint32 // TODO why C.RequestType doesn't work?
	Args        []string
	// Response converter
	Converter   func(any) any
}

func MakeCmd(requestType uint32, args []string, converter func(any) any) Cmd {
	return Cmd{RequestType: requestType, Args: args, Converter: converter}
}

func (b Batch) Convert(response []any) ([]any, error) {
	if len(response) != len(b.Commands) {
		return nil, &errors.RequestError{
			Msg: fmt.Sprintf("Response misaligned: received %d responses for %d commands", len(response), len(b.Commands)),
		}
	}
	for i, res := range response {
		response[i] = b.Commands[i].Converter(res)
	}
	return response, nil
}

type BatchOptions struct {
	Timeout              *uint32
	Route                *config.Route
	RetryServerError     *bool
	RetryConnectionError *bool
}

// get type of T
func GetType[T any]() reflect.Type {
	var zero [0]T
	return reflect.TypeOf(zero).Elem()
}

// ================================

// convert (typecast) untyped response into a typed value
// for example, an arbitrary array `[]any` into `[]string`
type responseConverter interface {
	convert(data any) any
}

// convert maps, T - type of the value, key is string
type mapConverter[T any] struct {
	next     responseConverter
	canBeNil bool
}

// convert arrays, T - type of the value
type arrayConverter[T any] struct {
	next     responseConverter
	canBeNil bool
}

// ================================

// Converts an untyped map into a map[string]T
func (node mapConverter[T]) convert(data any) any {
	if data == nil {
		if node.canBeNil {
			return nil
		} else {
			return &errors.RequestError{Msg: fmt.Sprintf("Unexpected type received: nil, expected: map[string]%v", GetType[T]())}
		}
	}
	result := make(map[string]T)

	// Iterate over the map and convert each value to T
	for key, value := range data.(map[string]any) {
		if node.next == nil {
			// try direct conversion to T when there is no next converter
			valueT, ok := value.(T)
			if !ok {
				return &errors.RequestError{
					Msg: fmt.Sprintf("Unexpected type of map element: %T, expected: %v", value, GetType[T]()),
				}
			}
			result[key] = valueT
		} else {
			// nested iteration when there is a next converter
			val := node.next.convert(value)
			if err, ok := val.(errors.RequestError); ok {
				return err
			}
			if val == nil {
				var null T
				result[key] = null
				continue
			}
			// convert to T
			valueT, ok := val.(T)
			if !ok {
				return &errors.RequestError{Msg: fmt.Sprintf("Unexpected type of map element: %T, expected: %v", val, GetType[T]())}
			}
			result[key] = valueT
		}
	}

	return result
}

// Converts an untyped array into a []T
func (node arrayConverter[T]) convert(data any) any {
	if data == nil {
		if node.canBeNil {
			return nil
		} else {
			return &errors.RequestError{Msg: fmt.Sprintf("Unexpected type received: nil, expected: []%v", GetType[T]())}
		}
	}
	arrData := data.([]any)
	result := make([]T, 0, len(arrData))
	for _, value := range arrData {
		if node.next == nil {
			valueT, ok := value.(T)
			if !ok {
				return &errors.RequestError{
					Msg: fmt.Sprintf("Unexpected type of array element: %T, expected: %v", value, GetType[T]()),
				}
			}
			result = append(result, valueT)
		} else {
			val := node.next.convert(value)
			if err, ok := val.(errors.RequestError); ok {
				return err
			}
			if val == nil {
				var null T
				result = append(result, null)
				continue
			}
			valueT, ok := val.(T)
			if !ok {
				return &errors.RequestError{Msg: fmt.Sprintf("Unexpected type of array element: %T, expected: %v", val, GetType[T]())}
			}
			result = append(result, valueT)
		}
	}

	return result
}

// ================================

func ConvertArrayOfNilOr[T any](data any) any {
	arr := data.([]any)
	res := make([]models.Result[T], 0, len(arr))

	for _, value := range arr {
		if value == nil {
			res = append(res, models.CreateNilResultOf[T]())
		} else {
			if val, ok := value.(T); ok {
				res = append(res, models.CreateResultOf[T](val))
			} else {
				res = append(res, models.CreateNilResultOf[T]())
			}
		}
	}
	return any(res)
}

func ConvertArrayOf[T any](data any) any {
	converted := arrayConverter[T]{
		nil,
		false,
	}.convert(data)
	if err, ok := converted.(*errors.RequestError); ok {
		return err
	}
	return converted
	// actually returns a []T
}

func ConvertMapOf[T any](data any) any {
	converted := mapConverter[T]{
		nil,
		false,
	}.convert(data)
	if err, ok := converted.(*errors.RequestError); ok {
		return err
	}
	return converted
	// actually returns a map[string]T
}

// BZPOPMAX BZPOPMIN
func ConvertKeyWithMemberAndScore(data any) any {
	arr := data.([]any)
	key := arr[0].(string)
	member := arr[1].(string)
	score := arr[2].(float64)
	return models.KeyWithMemberAndScore{Key: key, Member: member, Score: score}
}

// ZMPOP BZMPOP
func ConvertKeyWithArrayOfMembersAndScores(data any) any {
	if data == nil {
		return nil
	}

	arr := data.([]any)
	key := arr[0].(string)
	memberAndScoreArray := MakeConvertMapOfMemberAndScore(false)(arr[1]).([]models.MemberAndScore)

	return models.CreateKeyWithArrayOfMembersAndScoresResult(
		models.KeyWithArrayOfMembersAndScores{Key: key, MembersAndScores: memberAndScoreArray},
	)
}

// ZRangeWithScores ZInterWithScores ZDiffWithScores ZUnionWithScores
func MakeConvertMapOfMemberAndScore(reverse bool) func(data any) any {
	return func(data any) any {
		converted := ConvertMapOf[float64](data)
		if err, ok := converted.(*errors.RequestError); ok {
			return err
		}

		res := converted.(map[string]float64)
		memberAndScoreArray := make([]models.MemberAndScore, 0, len(res))

		for k, v := range res {
			memberAndScoreArray = append(memberAndScoreArray, models.MemberAndScore{Member: k, Score: v})
		}
		if !reverse {
			sort.Slice(memberAndScoreArray, func(i, j int) bool {
				if memberAndScoreArray[i].Score == memberAndScoreArray[j].Score {
					return memberAndScoreArray[i].Member < memberAndScoreArray[j].Member
				}
				return memberAndScoreArray[i].Score < memberAndScoreArray[j].Score
			})
		} else {
			sort.Slice(memberAndScoreArray, func(i, j int) bool {
				if memberAndScoreArray[i].Score == memberAndScoreArray[j].Score {
					return memberAndScoreArray[i].Member > memberAndScoreArray[j].Member
				}
				return memberAndScoreArray[i].Score > memberAndScoreArray[j].Score
			})
		}

		return memberAndScoreArray
	}
}

// ZRandMemberWithCountWithScores
func ConvertArrayOfMemberAndScore(data any) any {
	converted := arrayConverter[[]any]{
		arrayConverter[any]{
			nil,
			false,
		},
		false,
	}.convert(data)
	if err, ok := converted.(*errors.RequestError); ok {
		return err
	}
	pairs, ok := converted.([][]any)
	if !ok {
		return &errors.RequestError{Msg: fmt.Sprintf("unexpected type of data: %T", converted)}
	}
	memberAndScoreArray := make([]models.MemberAndScore, 0, len(pairs))
	for _, pair := range pairs {
		memberAndScoreArray = append(
			memberAndScoreArray,
			models.MemberAndScore{Member: pair[0].(string), Score: pair[1].(float64)},
		)
	}
	return memberAndScoreArray
}

// XAutoClaim XAutoClaimWithOptions
func ConvertXAutoClaimResponse(data any) any {
	arr := data.([]any)
	len := len(arr)
	if len < 2 || len > 3 {
		return &errors.RequestError{Msg: fmt.Sprintf("Unexpected response array length: %d", len)}
	}
	converted := mapConverter[[][]string]{
		arrayConverter[[]string]{
			arrayConverter[string]{
				nil,
				false,
			},
			false,
		},
		false,
	}.convert(arr[1])
	if err, ok := converted.(*errors.RequestError); ok {
		return err
	}

	claimedEntries, ok := converted.(map[string][][]string)
	if !ok {
		return &errors.RequestError{Msg: fmt.Sprintf("unexpected type of second element: %T", converted)}
	}
	var deletedMessages []string = nil
	if len == 3 {
		converted = arrayConverter[string]{
			nil,
			false,
		}.convert(arr[2])
		if err, ok := converted.(*errors.RequestError); ok {
			return err
		}
		deletedMessages, ok = converted.([]string)
		if !ok {
			return &errors.RequestError{Msg: fmt.Sprintf("unexpected type of third element: %T", converted)}
		}
	}
	return models.XAutoClaimResponse{
		NextEntry:       arr[0].(string),
		ClaimedEntries:  claimedEntries,
		DeletedMessages: deletedMessages,
	}
}

// XAutoClaimJustId XAutoClaimJustIdWithOptions
func ConvertXAutoClaimJustIdResponse(data any) any {
	arr := data.([]any)
	len := len(arr)
	if len < 2 || len > 3 {
		return &errors.RequestError{Msg: fmt.Sprintf("Unexpected response array length: %d", len)}
	}
	converted := arrayConverter[string]{
		nil,
		false,
	}.convert(arr[1])
	if err, ok := converted.(*errors.RequestError); ok {
		return err
	}

	claimedEntries, ok := converted.([]string)
	if !ok {
		return &errors.RequestError{Msg: fmt.Sprintf("unexpected type of second element: %T", converted)}
	}
	var deletedMessages []string = nil
	if len == 3 {
		converted = arrayConverter[string]{
			nil,
			false,
		}.convert(arr[2])
		if err, ok := converted.(*errors.RequestError); ok {
			return err
		}
		deletedMessages, ok = converted.([]string)
		if !ok {
			return &errors.RequestError{Msg: fmt.Sprintf("unexpected type of third element: %T", converted)}
		}
	}
	return models.XAutoClaimJustIdResponse{
		NextEntry:       arr[0].(string),
		ClaimedEntries:  claimedEntries,
		DeletedMessages: deletedMessages,
	}
}

// XInfoConsumers
func ConvertXInfoConsumersResponse(data any) any {
	converted := arrayConverter[map[string]any]{
		nil,
		false,
	}.convert(data)
	if err, ok := converted.(*errors.RequestError); ok {
		return err
	}
	arr, ok := converted.([]map[string]any)
	if !ok {
		return &errors.RequestError{Msg: fmt.Sprintf("unexpected type: %T", converted)}
	}

	result := make([]models.XInfoConsumerInfo, 0, len(arr))

	for _, group := range arr {
		info := models.XInfoConsumerInfo{
			Name:    group["name"].(string),
			Pending: group["pending"].(int64),
			Idle:    group["idle"].(int64),
		}
		switch inactive := group["inactive"].(type) {
		case int64:
			info.Inactive = models.CreateInt64Result(inactive)
		default:
			info.Inactive = models.CreateNilInt64Result()
		}
		result = append(result, info)
	}

	return result
}

// XInfoGroups
func ConvertXInfoGroupsResponse(data any) any {
	converted := arrayConverter[map[string]any]{
		nil,
		false,
	}.convert(data)
	if err, ok := converted.(*errors.RequestError); ok {
		return err
	}
	arr, ok := converted.([]map[string]any)
	if !ok {
		return &errors.RequestError{Msg: fmt.Sprintf("unexpected type: %T", converted)}
	}

	result := make([]models.XInfoGroupInfo, 0, len(arr))

	for _, group := range arr {
		info := models.XInfoGroupInfo{
			Name:            group["name"].(string),
			Consumers:       group["consumers"].(int64),
			Pending:         group["pending"].(int64),
			LastDeliveredId: group["last-delivered-id"].(string),
		}
		switch lag := group["lag"].(type) {
		case int64:
			info.Lag = models.CreateInt64Result(lag)
		default:
			info.Lag = models.CreateNilInt64Result()
		}
		switch entriesRead := group["entries-read"].(type) {
		case int64:
			info.EntriesRead = models.CreateInt64Result(entriesRead)
		default:
			info.EntriesRead = models.CreateNilInt64Result()
		}
		result = append(result, info)
	}

	return result
}

// XPending
func ConvertXPendingResponse(data any) any {
	arr := data.([]any)

	NumOfMessages := arr[0].(int64)
	var StartId, EndId models.Result[string]
	if arr[1] == nil {
		StartId = models.CreateNilStringResult()
	} else {
		StartId = models.CreateStringResult(arr[1].(string))
	}
	if arr[2] == nil {
		EndId = models.CreateNilStringResult()
	} else {
		EndId = models.CreateStringResult(arr[2].(string))
	}

	if pendingMessages, ok := arr[3].([]any); ok {
		var ConsumerPendingMessages []models.ConsumerPendingMessage
		for _, msg := range pendingMessages {
			consumerMessage := msg.([]any)
			count, err := strconv.ParseInt(consumerMessage[1].(string), 10, 64)
			if err == nil {
				ConsumerPendingMessages = append(ConsumerPendingMessages, models.ConsumerPendingMessage{
					ConsumerName: consumerMessage[0].(string),
					MessageCount: count,
				})
			}
		}
		return models.XPendingSummary{
			NumOfMessages:    NumOfMessages,
			StartId:          StartId,
			EndId:            EndId,
			ConsumerMessages: ConsumerPendingMessages,
		}
	} else {
		return models.XPendingSummary{NumOfMessages: NumOfMessages, StartId: StartId, EndId: EndId, ConsumerMessages: make([]models.ConsumerPendingMessage, 0)}
	}
}

// XPendingWithOptions
func ConvertXPendingWithOptionsResponse(data any) any {
	arr := data.([]any)
	pendingDetails := make([]models.XPendingDetail, 0, len(arr))

	for _, message := range arr {
		detail := message.([]any)

		pDetail := models.XPendingDetail{
			Id:            detail[0].(string),
			ConsumerName:  detail[1].(string),
			IdleTime:      detail[2].(int64),
			DeliveryCount: detail[3].(int64),
		}
		pendingDetails = append(pendingDetails, pDetail)
	}
	return pendingDetails
}

func Convert2DArrayOfString(data any) any {
	converted := arrayConverter[[]string]{
		arrayConverter[string]{
			nil,
			false,
		},
		false,
	}.convert(data)
	if err, ok := converted.(*errors.RequestError); ok {
		return err
	}
	return converted
	// actually returns a [][]string
}

// GeoPos
func Convert2DArrayOfFloat(data any) any {
	converted := arrayConverter[[]float64]{
		arrayConverter[float64]{
			nil,
			true,
		},
		false,
	}.convert(data)
	if err, ok := converted.(*errors.RequestError); ok {
		return err
	}
	return converted
	// actually returns a [][]float64
}

// GeoSearchWithFullOptions
func ConvertLocationArrayResponse(data any) any {
	converted := arrayConverter[[]any]{
		arrayConverter[any]{
			nil,
			false,
		},
		false,
	}.convert(data)
	if err, ok := converted.(*errors.RequestError); ok {
		return err
	}

	result := make([]options.Location, 0, len(converted.([][]any)))
	for _, responseArray := range converted.([][]any) {
		location := options.Location{
			Name: responseArray[0].(string),
		}

		additionalData := responseArray[1].([]any)
		for _, value := range additionalData {
			if v, ok := value.(float64); ok {
				location.Dist = v
			}
			if v, ok := value.(int64); ok {
				location.Hash = v
			}
			if coordArray, ok := value.([]any); ok {
				location.Coord = options.GeospatialData{
					Longitude: coordArray[0].(float64),
					Latitude:  coordArray[1].(float64),
				}
			}
		}
		result = append(result, location)
	}

	return result
}

// FunctionList
func ConvertFunctionListResponse(data any) any {
	result := make([]models.LibraryInfo, 0, len(data.([]any)))
	for _, item := range data.([]any) {
		if itemMap, ok := item.(map[string]any); ok {
			items := itemMap["functions"].([]any)
			functionInfo := make([]models.FunctionInfo, 0, len(items))
			for _, item := range items {
				if function, ok := item.(map[string]any); ok {
					// Handle nullable description
					var description string
					if desc, ok := function["description"].(string); ok {
						description = desc
					}

					// Handle flags map
					flags := make([]string, 0)
					if flagsMap, ok := function["flags"].(map[string]struct{}); ok {
						for flag := range flagsMap {
							flags = append(flags, flag)
						}
					}

					functionInfo = append(functionInfo, models.FunctionInfo{
						Name:        function["name"].(string),
						Description: description,
						Flags:       flags,
					})
				}
			}

			libraryInfo := models.LibraryInfo{
				Name:      itemMap["library_name"].(string),
				Engine:    itemMap["engine"].(string),
				Functions: functionInfo,
			}
			// Handle optional library_code field
			if code, ok := itemMap["library_code"].(string); ok {
				libraryInfo.Code = code
			}
			result = append(result, libraryInfo)
		}
	}
	return result
}

func ConvertLMPopResponse(data any) any {
	converted := mapConverter[[]string]{
		arrayConverter[string]{},
		false,
	}.convert(data)

	if err, ok := converted.(*errors.RequestError); ok {
		return err
	}
	return converted
	// actually returns a map[string][]string
}

func ConvertXReadResponse(data any) any {
	converted := mapConverter[map[string][][]string]{
		mapConverter[[][]string]{
			arrayConverter[[]string]{
				arrayConverter[string]{
					nil,
					false,
				},
				false,
			},
			false,
		},
		false,
	}.convert(data)

	if err, ok := converted.(*errors.RequestError); ok {
		return err
	}
	return converted
	// actually returns a map[string]map[string][][]string
}

func ConvertXReadGroupResponse(data any) any {
	converted := mapConverter[map[string][][]string]{
		mapConverter[[][]string]{
			arrayConverter[[]string]{
				arrayConverter[string]{
					nil,
					false,
				},
				true,
			},
			false,
		},
		false,
	}.convert(data)

	if err, ok := converted.(*errors.RequestError); ok {
		return err
	}
	return converted
	// actually returns a map[string]map[string][][]string
}

func ConvertXClaimResponse(data any) any {
	converted := mapConverter[[][]string]{
		arrayConverter[[]string]{
			arrayConverter[string]{
				nil,
				false,
			},
			false,
		},
		false,
	}.convert(data)

	if err, ok := converted.(*errors.RequestError); ok {
		return err
	}
	return converted
	// actually returns a map[string][][]string
}

func ConvertXRangeResponse(data any) any {
	converted := ConvertXClaimResponse(data)
	if err, ok := converted.(*errors.RequestError); ok {
		return err
	}
	claimedEntries := converted.(map[string][][]string)

	result := make([]models.XRangeResponse, 0, len(claimedEntries))

	for k, v := range claimedEntries {
		result = append(result, models.XRangeResponse{StreamId: k, Entries: v})
	}

	sort.Slice(result, func(i, j int) bool {
		return result[i].StreamId < result[j].StreamId
	})
	return result
}

func ConvertFunctionStatsResponse(data any) any {
	nodeMap := data.(map[string]any)
	// Process engines
	engines := make(map[string]models.Engine)
	if enginesMap, ok := nodeMap["engines"].(map[string]any); ok {
		for engineName, engineData := range enginesMap {
			if engineMap, ok := engineData.(map[string]any); ok {
				engine := models.Engine{
					Language:      engineName,
					FunctionCount: engineMap["functions_count"].(int64),
					LibraryCount:  engineMap["libraries_count"].(int64),
				}
				engines[engineName] = engine
			}
		}
	}

	// Process running script
	var runningScript models.RunningScript
	if scriptData := nodeMap["running_script"]; scriptData != nil {
		if scriptMap, ok := scriptData.(map[string]any); ok {
			runningScript = models.RunningScript{
				Name:     scriptMap["name"].(string),
				Cmd:      scriptMap["command"].(string),
				Args:     scriptMap["arguments"].([]string),
				Duration: time.Duration(scriptMap["duration_ms"].(int64)) * time.Millisecond,
			}
		}
	}

	return models.FunctionStatsResult{
		Engines:       engines,
		RunningScript: runningScript,
	}
}

func TypeChecker(data any, expectedType reflect.Kind, isNilable bool) any {
	return ConverterAndTypeChecker(data, expectedType, isNilable, func(res any) any { return res })
}

func ConverterAndTypeChecker(data any, expectedType reflect.Kind, isNilable bool, converter func(res any) any) any {
	if data == nil {
		if isNilable {
			return nil
		}
		return &errors.RequestError{
			Msg: fmt.Sprintf("Unexpected return type from Glide: got nil, expected %v", expectedType),
		}
	}
	if reflect.TypeOf(data).Kind() == expectedType {
		return converter(data)
	}
	if reflect.TypeOf(data).Kind() == reflect.TypeOf(&errors.RequestError{}).Kind() {
		// not converting a server error
		return data
	}
	// data lost even though it was incorrect
	// TODO maybe still return the data?
	return &errors.RequestError{
		Msg: fmt.Sprintf("Unexpected return type from Glide: got %v, expected %v", reflect.TypeOf(data), expectedType),
	}
}
