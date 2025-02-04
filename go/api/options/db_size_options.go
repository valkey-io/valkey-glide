package options

import "github.com/jamesx-improving/valkey-glide/go/api/config"

type DBSizeOptions struct {
	Route config.Route
}

func NewTimeOptionsBuilder() *DBSizeOptions {
	return &DBSizeOptions{}
}

func (dbSizeOptions *DBSizeOptions) SetRoute(route config.Route) *DBSizeOptions {
	dbSizeOptions.Route = route
	return dbSizeOptions
}
