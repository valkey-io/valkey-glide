// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

package integTest

import (
	"sort"
	"testing"
	"time"

	"github.com/stretchr/testify/assert"
	"github.com/valkey-io/valkey-glide/go/api"
)

// TestPubSubChannels tests the PubSubChannels command for standalone client
func (suite *GlideTestSuite) TestPubSub_Commands_Channels() {
	tests := []struct {
		name          string
		clientType    ClientType
		channelNames  []string
		pattern       string
		expectedNames []string
	}{
		{
			name:          "Standalone Empty Pattern",
			clientType:    GlideClient,
			channelNames:  []string{"news.sports", "news.weather", "events.local"},
			pattern:       "",
			expectedNames: []string{"news.sports", "news.weather", "events.local"},
		},
		{
			name:          "Standalone Exact Match",
			clientType:    GlideClient,
			channelNames:  []string{"news.sports", "news.weather", "events.local"},
			pattern:       "news.sports",
			expectedNames: []string{"news.sports"},
		},
		{
			name:          "Standalone Glob Pattern",
			clientType:    GlideClient,
			channelNames:  []string{"news.sports", "news.weather", "events.local"},
			pattern:       "news.*",
			expectedNames: []string{"news.sports", "news.weather"},
		},
		{
			name:          "Cluster Empty Pattern",
			clientType:    GlideClusterClient,
			channelNames:  []string{"cluster.news.sports", "cluster.news.weather", "cluster.events.local"},
			pattern:       "",
			expectedNames: []string{"cluster.news.sports", "cluster.news.weather", "cluster.events.local"},
		},
		{
			name:          "Cluster Exact Match",
			clientType:    GlideClusterClient,
			channelNames:  []string{"cluster.news.sports", "cluster.news.weather", "cluster.events.local"},
			pattern:       "cluster.news.sports",
			expectedNames: []string{"cluster.news.sports"},
		},
		{
			name:          "Cluster Glob Pattern",
			clientType:    GlideClusterClient,
			channelNames:  []string{"cluster.news.sports", "cluster.news.weather", "cluster.events.local"},
			pattern:       "cluster.news.*",
			expectedNames: []string{"cluster.news.sports", "cluster.news.weather"},
		},
	}

	for _, tt := range tests {
		suite.T().Run(tt.name, func(t *testing.T) {
			// Create channel definitions for all channels
			channels := make([]ChannelDefn, len(tt.channelNames))
			for i, channelName := range tt.channelNames {
				channels[i] = ChannelDefn{Channel: channelName, Mode: 0} // ExactMode
			}

			// Create a client with subscriptions
			receiver := suite.CreatePubSubReceiver(tt.clientType, channels, 1, false)
			t.Cleanup(func() { receiver.Close() })

			// Allow subscription to establish
			time.Sleep(MESSAGE_PROCESSING_DELAY * time.Millisecond)

			// Get active channels
			var activeChannels []string
			var err error
			if tt.pattern == "" {
				activeChannels, err = receiver.PubSubChannels()
			} else {
				activeChannels, err = receiver.PubSubChannelsWithPattern(tt.pattern)
			}
			assert.NoError(t, err)

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
			clientType:    GlideClient,
			channelDefns:  []ChannelDefn{{Channel: "news.*", Mode: PatternMode}},
			expectedCount: 1,
		},
		{
			name:       "Standalone Multiple Patterns",
			clientType: GlideClient,
			channelDefns: []ChannelDefn{
				{Channel: "news.*", Mode: PatternMode},
				{Channel: "events.*", Mode: PatternMode},
				{Channel: "sports.*", Mode: PatternMode},
			},
			expectedCount: 3,
		},
		{
			name:       "Standalone Mixed Modes",
			clientType: GlideClient,
			channelDefns: []ChannelDefn{
				{Channel: "news.*", Mode: PatternMode},
				{Channel: "events.local", Mode: ExactMode},
				{Channel: "sports.*", Mode: PatternMode},
			},
			expectedCount: 2,
		},
		{
			name:          "Cluster Single Pattern",
			clientType:    GlideClusterClient,
			channelDefns:  []ChannelDefn{{Channel: "cluster.news.*", Mode: PatternMode}},
			expectedCount: 1,
		},
		{
			name:       "Cluster Multiple Patterns",
			clientType: GlideClusterClient,
			channelDefns: []ChannelDefn{
				{Channel: "cluster.news.*", Mode: PatternMode},
				{Channel: "cluster.events.*", Mode: PatternMode},
				{Channel: "cluster.sports.*", Mode: PatternMode},
			},
			expectedCount: 3,
		},
		{
			name:       "Cluster Mixed Modes",
			clientType: GlideClusterClient,
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
			receiver := suite.CreatePubSubReceiver(tt.clientType, tt.channelDefns, 1, false)
			t.Cleanup(func() { receiver.Close() })

			// Allow subscription to establish
			time.Sleep(MESSAGE_PROCESSING_DELAY * time.Millisecond)

			// Get pattern subscription count
			count, err := receiver.PubSubNumPat()
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
	}{
		{
			name:          "Standalone Single Channel",
			clientType:    GlideClient,
			channelDefns:  []ChannelDefn{{Channel: "news.sports", Mode: ExactMode}},
			queryChannels: []string{"news.sports"},
			expectedCounts: map[string]int64{
				"news.sports": 1,
			},
		},
		{
			name:       "Standalone Multiple Channels",
			clientType: GlideClient,
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
		},
		{
			name:       "Standalone Mixed Modes",
			clientType: GlideClient,
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
		},
		{
			name:          "Cluster Single Channel",
			clientType:    GlideClusterClient,
			channelDefns:  []ChannelDefn{{Channel: "cluster.news.sports", Mode: ExactMode}},
			queryChannels: []string{"cluster.news.sports"},
			expectedCounts: map[string]int64{
				"cluster.news.sports": 1,
			},
		},
		{
			name:       "Cluster Multiple Channels",
			clientType: GlideClusterClient,
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
		},
		{
			name:       "Cluster Mixed Modes",
			clientType: GlideClusterClient,
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
		},
	}

	for _, tt := range tests {
		suite.T().Run(tt.name, func(t *testing.T) {
			clients := make([]api.BaseClient, 0, len(tt.channelDefns))
			for _, defn := range tt.channelDefns {
				client := suite.CreatePubSubReceiver(tt.clientType, []ChannelDefn{defn}, 1, false)
				clients = append(clients, client)
				t.Cleanup(func() { client.Close() })
			}

			// Allow subscriptions to establish
			time.Sleep(MESSAGE_PROCESSING_DELAY * time.Millisecond)

			// Get subscriber counts using the first client
			counts, err := clients[0].PubSubNumSub(tt.queryChannels)
			assert.NoError(t, err)

			// Verify counts match expected values
			assert.Equal(t, tt.expectedCounts, counts)
		})
	}
}
