// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

package integTest

import (
	"sort"
	"testing"
	"time"

	"github.com/stretchr/testify/assert"
)

// TestPubSubChannels tests the PubSubChannels command for standalone client
func (suite *GlideTestSuite) TestPubSubChannels() {
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
			defer receiver.Close()

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
func (suite *GlideTestSuite) TestPubSubNumPat() {
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
			defer receiver.Close()

			// Allow subscription to establish
			time.Sleep(MESSAGE_PROCESSING_DELAY * time.Millisecond)

			// Get pattern subscription count
			count, err := receiver.PubSubNumPat()
			assert.NoError(t, err)
			assert.Equal(t, tt.expectedCount, count)
		})
	}
}
