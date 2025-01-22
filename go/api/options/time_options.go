package options

import "github.com/valkey-io/valkey-glide/go/glide/api/config"

type TimeOptions struct {
	Route config.Route
}

func NewTimeOptionsBuilder() *TimeOptions {
	return &TimeOptions{}
}

func (timeOptions *TimeOptions) SetRoute(route config.Route) *TimeOptions {
	timeOptions.Route = route
	return timeOptions
}
