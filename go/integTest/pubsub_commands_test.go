// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

package integTest

import (
	"context"
	"sort"
	"testing"
	"time"

	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
	glide "github.com/valkey-io/valkey-glide/go/v2"
	"github.com/valkey-io/valkey-glide/go/v2/internal/interfaces"
)

// TestPubSubChannels tests the PubSubChannels command for standalone client
func (suite *GlideTestSuite) TestPubSub_Commands_Channels() {
	tests := []struct {
		name          string
		clientType    ClientType
		channelNames  []string
		pattern       string
		expectedNames []string
		sharded       bool
	}{
		{
			name:          "Standalone Empty Pattern",
			clientType:    StandaloneClient,
			channelNames:  []string{"news.sports", "news.weather", "events.local"},
			pattern:       "",
			expectedNames: []string{"news.sports", "news.weather", "events.local"},
			sharded:       false,
		},
		{
			name:          "Standalone Exact Match",
			clientType:    StandaloneClient,
			channelNames:  []string{"news.sports", "news.weather", "events.local"},
			pattern:       "news.sports",
			expectedNames: []string{"news.sports"},
			sharded:       false,
		},
		{
			name:          "Standalone Glob Pattern",
			clientType:    StandaloneClient,
			channelNames:  []string{"news.sports", "news.weather", "events.local"},
			pattern:       "news.*",
			expectedNames: []string{"news.sports", "news.weather"},
			sharded:       false,
		},
		{
			name:          "Cluster Empty Pattern",
			clientType:    ClusterClient,
			channelNames:  []string{"cluster.news.sports", "cluster.news.weather", "cluster.events.local"},
			pattern:       "",
			expectedNames: []string{"cluster.news.sports", "cluster.news.weather", "cluster.events.local"},
			sharded:       false,
		},
		{
			name:          "Cluster Exact Match",
			clientType:    ClusterClient,
			channelNames:  []string{"cluster.news.sports", "cluster.news.weather", "cluster.events.local"},
			pattern:       "cluster.news.sports",
			expectedNames: []string{"cluster.news.sports"},
			sharded:       false,
		},
		{
			name:          "Cluster Glob Pattern",
			clientType:    ClusterClient,
			channelNames:  []string{"cluster.news.sports", "cluster.news.weather", "cluster.events.local"},
			pattern:       "cluster.news.*",
			expectedNames: []string{"cluster.news.sports", "cluster.news.weather"},
			sharded:       false,
		},
		{
			name:          "Cluster Sharded Empty Pattern",
			clientType:    ClusterClient,
			channelNames:  []string{"cluster.shard.news.sports", "cluster.shard.news.weather", "cluster.shard.events.local"},
			pattern:       "",
			expectedNames: []string{"cluster.shard.news.sports", "cluster.shard.news.weather", "cluster.shard.events.local"},
			sharded:       true,
		},
		{
			name:          "Cluster Sharded Exact Match",
			clientType:    ClusterClient,
			channelNames:  []string{"cluster.shard.news.sports", "cluster.shard.news.weather", "cluster.shard.events.local"},
			pattern:       "cluster.shard.news.sports",
			expectedNames: []string{"cluster.shard.news.sports"},
			sharded:       true,
		},
		{
			name:          "Cluster Sharded Glob Pattern",
			clientType:    ClusterClient,
			channelNames:  []string{"cluster.shard.news.sports", "cluster.shard.news.weather", "cluster.shard.events.local"},
			pattern:       "cluster.shard.news.*",
			expectedNames: []string{"cluster.shard.news.sports", "cluster.shard.news.weather"},
			sharded:       true,
		},
	}

	for _, tt := range tests {
		suite.T().Run(tt.name, func(t *testing.T) {
			if tt.sharded {
				suite.SkipIfServerVersionLowerThan("7.0.0", t)
			}
			// Create channel definitions for all channels
			channels := make([]ChannelDefn, len(tt.channelNames))
			for i, channelName := range tt.channelNames {
				channels[i] = ChannelDefn{Channel: channelName, Mode: getChannelMode(tt.sharded)}
			}

			// Create a client with subscriptions
			receiver := suite.CreatePubSubReceiver(tt.clientType, channels, 1, false, ConfigMethod, t)
			t.Cleanup(func() { receiver.Close() })

			// Allow subscription to establish
			time.Sleep(MESSAGE_PROCESSING_DELAY * time.Millisecond)

			// Get active channels
			var activeChannels []string
			var err error
			if tt.sharded {
				// For sharded channels, we need to use the cluster-specific methods
				clusterClient, ok := receiver.(*glide.ClusterClient)
				require.True(t, ok, "Expected GlideClusterClient for sharded channels")
				if tt.pattern == "" {
					activeChannels, err = clusterClient.PubSubShardChannels(context.Background())
				} else {
					activeChannels, err = clusterClient.PubSubShardChannelsWithPattern(context.Background(), tt.pattern)
				}
			} else {
				if tt.pattern == "" {
					activeChannels, err = receiver.PubSubChannels(context.Background())
				} else {
					activeChannels, err = receiver.PubSubChannelsWithPattern(context.Background(), tt.pattern)
				}
			}
			require.NoError(t, err)

			// Sort both slices for consistent comparison
			sort.Strings(activeChannels)
			sort.Strings(tt.expectedNames)

			// Verify using the verification function
			assert.Equal(t, tt.expectedNames, activeChannels)
		})
	}
}

// TestPubSubNumPat tests the PubSubNumPat command for standalone and cluster clients
func (suite *GlideTestSuite) TestPubSub_Commands_NumPat() {
	tests := []struct {
		name          string
		clientType    ClientType
		channelDefns  []ChannelDefn
		expectedCount int64
	}{
		{
			name:          "Standalone Single Pattern",
			clientType:    StandaloneClient,
			channelDefns:  []ChannelDefn{{Channel: "news.*", Mode: PatternMode}},
			expectedCount: 1,
		},
		{
			name:       "Standalone Multiple Patterns",
			clientType: StandaloneClient,
			channelDefns: []ChannelDefn{
				{Channel: "news.*", Mode: PatternMode},
				{Channel: "events.*", Mode: PatternMode},
				{Channel: "sports.*", Mode: PatternMode},
			},
			expectedCount: 3,
		},
		{
			name:       "Standalone Mixed Modes",
			clientType: StandaloneClient,
			channelDefns: []ChannelDefn{
				{Channel: "news.*", Mode: PatternMode},
				{Channel: "events.local", Mode: ExactMode},
				{Channel: "sports.*", Mode: PatternMode},
			},
			expectedCount: 2,
		},
		{
			name:          "Cluster Single Pattern",
			clientType:    ClusterClient,
			channelDefns:  []ChannelDefn{{Channel: "cluster.news.*", Mode: PatternMode}},
			expectedCount: 1,
		},
		{
			name:       "Cluster Multiple Patterns",
			clientType: ClusterClient,
			channelDefns: []ChannelDefn{
				{Channel: "cluster.news.*", Mode: PatternMode},
				{Channel: "cluster.events.*", Mode: PatternMode},
				{Channel: "cluster.sports.*", Mode: PatternMode},
			},
			expectedCount: 3,
		},
		{
			name:       "Cluster Mixed Modes",
			clientType: ClusterClient,
			channelDefns: []ChannelDefn{
				{Channel: "cluster.news.*", Mode: PatternMode},
				{Channel: "cluster.events.local", Mode: ExactMode},
				{Channel: "cluster.sports.*", Mode: PatternMode},
			},
			expectedCount: 2,
		},
	}

	for _, tt := range tests {
		suite.T().Run(tt.name, func(t *testing.T) {
			// Create a client with subscriptions
			receiver := suite.CreatePubSubReceiver(tt.clientType, tt.channelDefns, 1, false, ConfigMethod, t)
			t.Cleanup(func() { receiver.Close() })

			// Allow subscription to establish
			time.Sleep(MESSAGE_PROCESSING_DELAY * time.Millisecond)

			// Get pattern subscription count
			count, err := receiver.PubSubNumPat(context.Background())
			assert.NoError(t, err)
			assert.Equal(t, tt.expectedCount, count)
		})
	}
}

// TestPubSubNumSub tests the PubSubNumSub command for standalone and cluster clients
func (suite *GlideTestSuite) TestPubSub_Commands_NumSub() {
	tests := []struct {
		name           string
		clientType     ClientType
		channelDefns   []ChannelDefn
		queryChannels  []string
		expectedCounts map[string]int64
		sharded        bool
	}{
		{
			name:          "Standalone Single Channel",
			clientType:    StandaloneClient,
			channelDefns:  []ChannelDefn{{Channel: "news.sports", Mode: ExactMode}},
			queryChannels: []string{"news.sports"},
			expectedCounts: map[string]int64{
				"news.sports": 1,
			},
			sharded: false,
		},
		{
			name:       "Standalone Multiple Channels",
			clientType: StandaloneClient,
			channelDefns: []ChannelDefn{
				{Channel: "news.sports", Mode: ExactMode},
				{Channel: "news.weather", Mode: ExactMode},
				{Channel: "news.weather", Mode: ExactMode}, // Second subscriber
				{Channel: "events.local", Mode: ExactMode},
			},
			queryChannels: []string{"news.sports", "news.weather", "events.local"},
			expectedCounts: map[string]int64{
				"news.sports":  1,
				"news.weather": 2,
				"events.local": 1,
			},
			sharded: false,
		},
		{
			name:       "Standalone Mixed Modes",
			clientType: StandaloneClient,
			channelDefns: []ChannelDefn{
				{Channel: "news.*", Mode: PatternMode},
				{Channel: "events.local", Mode: ExactMode},
				{Channel: "sports.*", Mode: PatternMode},
			},
			queryChannels: []string{"news.sports", "events.local", "sports.football"},
			expectedCounts: map[string]int64{
				"news.sports":     0, // Pattern subscribers don't count for exact channel queries
				"events.local":    1,
				"sports.football": 0, // Pattern subscribers don't count for exact channel queries
			},
			sharded: false,
		},
		{
			name:          "Cluster Single Channel",
			clientType:    ClusterClient,
			channelDefns:  []ChannelDefn{{Channel: "cluster.news.sports", Mode: ExactMode}},
			queryChannels: []string{"cluster.news.sports"},
			expectedCounts: map[string]int64{
				"cluster.news.sports": 1,
			},
			sharded: false,
		},
		{
			name:       "Cluster Multiple Channels",
			clientType: ClusterClient,
			channelDefns: []ChannelDefn{
				{Channel: "cluster.news.sports", Mode: ExactMode},
				{Channel: "cluster.news.weather", Mode: ExactMode},
				{Channel: "cluster.news.weather", Mode: ExactMode}, // Second subscriber
				{Channel: "cluster.events.local", Mode: ExactMode},
			},
			queryChannels: []string{"cluster.news.sports", "cluster.news.weather", "cluster.events.local"},
			expectedCounts: map[string]int64{
				"cluster.news.sports":  1,
				"cluster.news.weather": 2,
				"cluster.events.local": 1,
			},
			sharded: false,
		},
		{
			name:       "Cluster Mixed Modes",
			clientType: ClusterClient,
			channelDefns: []ChannelDefn{
				{Channel: "cluster.news.*", Mode: PatternMode},
				{Channel: "cluster.events.local", Mode: ExactMode},
				{Channel: "cluster.sports.*", Mode: PatternMode},
			},
			queryChannels: []string{"cluster.news.sports", "cluster.events.local", "cluster.sports.football"},
			expectedCounts: map[string]int64{
				"cluster.news.sports":     0, // Pattern subscribers don't count for exact channel queries
				"cluster.events.local":    1,
				"cluster.sports.football": 0, // Pattern subscribers don't count for exact channel queries
			},
			sharded: false,
		},
		{
			name:          "Cluster Sharded Single Channel",
			clientType:    ClusterClient,
			channelDefns:  []ChannelDefn{{Channel: "cluster.shard.news.sports", Mode: ShardedMode}},
			queryChannels: []string{"cluster.shard.news.sports"},
			expectedCounts: map[string]int64{
				"cluster.shard.news.sports": 1,
			},
			sharded: true,
		},
		{
			name:       "Cluster Sharded Multiple Channels",
			clientType: ClusterClient,
			channelDefns: []ChannelDefn{
				{Channel: "cluster.shard.news.sports", Mode: ShardedMode},
				{Channel: "cluster.shard.news.weather", Mode: ShardedMode},
				{Channel: "cluster.shard.news.weather", Mode: ShardedMode}, // Second subscriber
				{Channel: "cluster.shard.events.local", Mode: ShardedMode},
			},
			queryChannels: []string{"cluster.shard.news.sports", "cluster.shard.news.weather", "cluster.shard.events.local"},
			expectedCounts: map[string]int64{
				"cluster.shard.news.sports":  1,
				"cluster.shard.news.weather": 2,
				"cluster.shard.events.local": 1,
			},
			sharded: true,
		},
		{
			name:       "Cluster Sharded Mixed Modes",
			clientType: ClusterClient,
			channelDefns: []ChannelDefn{
				{Channel: "cluster.shard.news.*", Mode: PatternMode},
				{Channel: "cluster.shard.events.local", Mode: ShardedMode},
				{Channel: "cluster.shard.sports.*", Mode: PatternMode},
			},
			queryChannels: []string{
				"cluster.shard.news.sports",
				"cluster.shard.events.local",
				"cluster.shard.sports.football",
			},
			expectedCounts: map[string]int64{
				"cluster.shard.news.sports":     0, // Pattern subscribers don't count for exact channel queries
				"cluster.shard.events.local":    1,
				"cluster.shard.sports.football": 0, // Pattern subscribers don't count for exact channel queries
			},
			sharded: true,
		},
	}

	for _, tt := range tests {
		suite.T().Run(tt.name, func(t *testing.T) {
			if tt.sharded {
				suite.SkipIfServerVersionLowerThan("7.0.0", t)
			}

			clients := make([]interfaces.BaseClientCommands, 0, len(tt.channelDefns))
			for _, defn := range tt.channelDefns {
				client := suite.CreatePubSubReceiver(tt.clientType, []ChannelDefn{defn}, 1, false, ConfigMethod, t)
				clients = append(clients, client)
				t.Cleanup(func() { client.Close() })
			}

			// Allow subscriptions to establish
			time.Sleep(MESSAGE_PROCESSING_DELAY * time.Millisecond)

			// Get subscriber counts using the first client
			var counts map[string]int64
			var err error
			if tt.sharded {
				// For sharded channels, we need to use the cluster-specific methods
				clusterClient, ok := clients[0].(*glide.ClusterClient)
				require.True(t, ok, "Expected GlideClusterClient for sharded channels")
				counts, err = clusterClient.PubSubShardNumSub(context.Background(), tt.queryChannels...)
			} else {
				counts, err = clients[0].PubSubNumSub(context.Background(), tt.queryChannels...)
			}
			require.NoError(t, err)

			// Verify counts match expected values
			assert.Equal(t, tt.expectedCounts, counts)
		})
	}
}
