/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.protobuf;

/**
 * Compatibility stub for protobuf CommandRequest class.
 * This provides basic compatibility for tests that reference the old protobuf-based implementation.
 */
public class CommandRequest {
    
    /**
     * Builder for CommandRequest compatibility.
     */
    public static class Builder {
        private CommandRequestOuterClass.RequestType requestType;
        private String[] args;
        
        public Builder() {
            this.requestType = CommandRequestOuterClass.RequestType.InvalidRequest;
            this.args = new String[0];
        }
        
        public Builder setRequestType(CommandRequestOuterClass.RequestType requestType) {
            this.requestType = requestType;
            return this;
        }
        
        public Builder addArgs(String... args) {
            this.args = args;
            return this;
        }
        
        public CommandRequest build() {
            return new CommandRequest(requestType, args);
        }
    }
    
    private final CommandRequestOuterClass.RequestType requestType;
    private final String[] args;
    
    private CommandRequest(CommandRequestOuterClass.RequestType requestType, String[] args) {
        this.requestType = requestType;
        this.args = args;
    }
    
    public CommandRequestOuterClass.RequestType getRequestType() {
        return requestType;
    }
    
    public String[] getArgs() {
        return args;
    }
    
    public static Builder newBuilder() {
        return new Builder();
    }
    
    /**
     * Compatibility stub for command request types.
     */
    public static class CommandRequestOuterClass {
        
        /**
         * Compatibility stub for RequestType enum.
         */
        public enum RequestType {
            InvalidRequest,
            GetRequest,
            SetRequest,
            PingRequest,
            InfoRequest,
            DelRequest,
            ExistsRequest,
            MGetRequest,
            MSetRequest,
            IncrRequest,
            IncrByRequest,
            DecrRequest,
            DecrByRequest,
            StrlenRequest,
            SetRangeRequest,
            GetRangeRequest,
            AppendRequest,
            LPushRequest,
            LPopRequest,
            RPushRequest,
            RPopRequest,
            LLenRequest,
            LRemRequest,
            LRangeRequest,
            LTrimRequest,
            LIndexRequest,
            LInsertRequest,
            LSetRequest,
            SAddRequest,
            SRemRequest,
            SMembersRequest,
            SCardRequest,
            SInterRequest,
            SInterStoreRequest,
            SUnionRequest,
            SUnionStoreRequest,
            SDiffRequest,
            SDiffStoreRequest,
            SIsMemberRequest,
            SPopRequest,
            SRandMemberRequest,
            HSetRequest,
            HGetRequest,
            HDelRequest,
            HMSetRequest,
            HMGetRequest,
            HExistsRequest,
            HGetAllRequest,
            HIncrByRequest,
            HIncrByFloatRequest,
            HKeysRequest,
            HValsRequest,
            HLenRequest,
            ZAddRequest,
            ZRemRequest,
            ZCardRequest,
            ZCountRequest,
            ZIncrByRequest,
            ZInterStoreRequest,
            ZRangeRequest,
            ZRangeByIndexRequest,
            ZRangeByLexRequest,
            ZRangeByScoreRequest,
            ZRankRequest,
            ZRemRangeByIndexRequest,
            ZRemRangeByLexRequest,
            ZRemRangeByScoreRequest,
            ZRevRangeRequest,
            ZRevRangeByScoreRequest,
            ZRevRankRequest,
            ZScoreRequest,
            ZUnionStoreRequest,
            XAddRequest,
            XReadRequest,
            XLenRequest,
            XDelRequest,
            XRangeRequest,
            XRevRangeRequest,
            XGroupCreateRequest,
            XGroupDestroyRequest,
            XGroupCreateConsumerRequest,
            XGroupDelConsumerRequest,
            XReadGroupRequest,
            XAckRequest,
            XClaimRequest,
            XPendingRequest,
            XTrimRequest,
            XInfoStreamRequest,
            XInfoGroupsRequest,
            XInfoConsumersRequest,
            TimeRequest,
            FlushAllRequest,
            FlushDBRequest,
            LMoveRequest,
            BLPopRequest,
            BRPopRequest,
            LPosRequest,
            BLMoveRequest,
            ZPopMinRequest,
            ZPopMaxRequest,
            BZPopMinRequest,
            BZPopMaxRequest,
            ZMPopRequest,
            BZMPopRequest,
            ZRandMemberRequest,
            ZInterRequest,
            ZUnionRequest,
            ZDiffRequest,
            ZDiffStoreRequest,
            ZMScoreRequest,
            ZScanRequest,
            HScanRequest,
            SScanRequest,
            ScanRequest,
            GeoAddRequest,
            GeoDistRequest,
            GeoHashRequest,
            GeoPosRequest,
            GeoRadiusRequest,
            GeoRadiusByMemberRequest,
            GeoSearchRequest,
            GeoSearchStoreRequest,
            BitCountRequest,
            BitFieldRequest,
            BitOpRequest,
            BitPosRequest,
            GetBitRequest,
            SetBitRequest,
            PfAddRequest,
            PfCountRequest,
            PfMergeRequest,
            ObjectEncodingRequest,
            ObjectFreqRequest,
            ObjectIdleTimeRequest,
            ObjectRefCountRequest,
            TouchRequest,
            UnlinkRequest,
            TTLRequest,
            PTTLRequest,
            ExpireRequest,
            ExpireAtRequest,
            PExpireRequest,
            PExpireAtRequest,
            PersistRequest,
            TypeRequest,
            RenameRequest,
            RenameNXRequest,
            DBSizeRequest,
            KeysRequest,
            RandomKeyRequest,
            SortRequest,
            SortRORequest,
            LastSaveRequest,
            ConfigGetRequest,
            ConfigSetRequest,
            ConfigResetStatRequest,
            ConfigRewriteRequest,
            ClientIdRequest,
            ClientInfoRequest,
            ClientListRequest,
            ClientGetNameRequest,
            ClientSetNameRequest,
            ClientPauseRequest,
            ClientUnpauseRequest,
            ClientKillRequest,
            ClientUnblockRequest,
            ClientGetRedir,
            ClientTrackingRequest,
            ClientTrackingInfoRequest,
            ClientCachingRequest,
            ClientGetredir,
            ClientNoEvictRequest,
            ClientReplyRequest,
            ClientSetinfoRequest,
            EchoRequest,
            SelectRequest,
            MoveRequest,
            CopyRequest,
            SwapDBRequest,
            RestoreRequest,
            DumpRequest,
            MigrateRequest,
            WaitRequest,
            PubSubChannelsRequest,
            PubSubNumsubRequest,
            PubSubNumpatRequest,
            PubSubShardChannelsRequest,
            PubSubShardNumsubRequest,
            EvalRequest,
            EvalShaRequest,
            EvalRORequest,
            EvalShaRORequest,
            ScriptExistsRequest,
            ScriptFlushRequest,
            ScriptKillRequest,
            ScriptLoadRequest,
            FunctionLoadRequest,
            FunctionListRequest,
            FunctionFlushRequest,
            FunctionDeleteRequest,
            FunctionStatsRequest,
            FunctionKillRequest,
            FunctionDumpRequest,
            FunctionRestoreRequest,
            FCallRequest,
            FCallRORequest,
            MemoryUsageRequest,
            MemoryStatsRequest,
            MemoryPurgeRequest,
            LatencyHistogramRequest,
            LatencyLatestRequest,
            LatencyResetRequest,
            LatencyDoctorRequest,
            ModuleListRequest,
            ModuleLoadRequest,
            ModuleUnloadRequest,
            SlowlogGetRequest,
            SlowlogLenRequest,
            SlowlogResetRequest,
            MonitorRequest,
            ResetRequest,
            QuitRequest,
            ShutdownRequest,
            ClusterAddSlotsRequest,
            ClusterCountFailureReportsRequest,
            ClusterCountKeysInSlotRequest,
            ClusterDelSlotsRequest,
            ClusterFailoverRequest,
            ClusterForgetRequest,
            ClusterGetKeysInSlotRequest,
            ClusterInfoRequest,
            ClusterKeySlotRequest,
            ClusterMeetRequest,
            ClusterNodesRequest,
            ClusterReplicateRequest,
            ClusterResetRequest,
            ClusterSaveConfigRequest,
            ClusterSetConfigEpochRequest,
            ClusterShardsRequest,
            ClusterSlotsRequest,
            ClusterSetSlotRequest,
            ReadOnlyRequest,
            ReadWriteRequest,
            JsonArrAppendRequest,
            JsonArrIndexRequest,
            JsonArrInsertRequest,
            JsonArrLenRequest,
            JsonArrPopRequest,
            JsonArrTrimRequest,
            JsonClearRequest,
            JsonDelRequest,
            JsonGetRequest,
            JsonMGetRequest,
            JsonNumincrbyRequest,
            JsonNummultbyRequest,
            JsonObjkeysRequest,
            JsonObjlenRequest,
            JsonSetRequest,
            JsonStrappendRequest,
            JsonStrlenRequest,
            JsonToggleRequest,
            JsonTypeRequest,
            JsonForgetRequest,
            JsonRespRequest,
            JsonDebugRequest,
            JsonMergeRequest,
            JsonMSetRequest,
            FtCreateRequest,
            FtDropIndexRequest,
            FtSearchRequest,
            FtAggregateRequest,
            FtProfileRequest,
            FtExplainRequest,
            FtExplainCLIRequest,
            FtConfigGetRequest,
            FtConfigSetRequest,
            FtListRequest,
            FtInfoRequest,
            FtSpellCheckRequest,
            FtDictAddRequest,
            FtDictDelRequest,
            FtDictDumpRequest,
            FtSynAddRequest,
            FtSynUpdateRequest,
            FtSynDumpRequest,
            FtAliasAddRequest,
            FtAliasDelRequest,
            FtAliasUpdateRequest,
            FtTagValsRequest,
            FtSugAddRequest,
            FtSugDelRequest,
            FtSugGetRequest,
            FtSugLenRequest,
            CustomCommand
        }
        
        public static RequestType valueOf(String name) {
            try {
                return RequestType.valueOf(name);
            } catch (IllegalArgumentException e) {
                return RequestType.InvalidRequest;
            }
        }
    }
}