// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

package options

import (
	"fmt"

	"github.com/valkey-io/valkey-glide/go/v2/constants"
)

// MigrateOptions represents optional arguments for the Migrate and MigrateWithOptions commands.
//
// See [valkey.io] for details.
//
// [valkey.io]: https://valkey.io/commands/migrate/
type MigrateOptions struct {
	// If true, do not remove the key from the source instance.
	Copy bool
	// If true, replace the existing key on the destination instance.
	Replace bool
	// Authentication password for the destination instance.
	Password string
	// Authentication username for the destination instance (requires Password).
	Username string
}

// NewMigrateOptions creates a new MigrateOptions with default values.
func NewMigrateOptions() *MigrateOptions {
	return &MigrateOptions{}
}

// SetCopy sets the COPY flag.
func (o *MigrateOptions) SetCopy() *MigrateOptions {
	o.Copy = true
	return o
}

// SetReplace sets the REPLACE flag.
func (o *MigrateOptions) SetReplace() *MigrateOptions {
	o.Replace = true
	return o
}

// SetPassword sets the AUTH password. Clears any previously set username.
func (o *MigrateOptions) SetPassword(password string) *MigrateOptions {
	o.Password = password
	o.Username = "" // clear username to avoid ambiguous AUTH2 state
	return o
}

// SetAuth2 sets the AUTH2 username and password.
func (o *MigrateOptions) SetAuth2(username, password string) *MigrateOptions {
	o.Username = username
	o.Password = password
	return o
}

// ToArgs converts MigrateOptions to a string slice for use in the MIGRATE command.
func (o *MigrateOptions) ToArgs() ([]string, error) {
	args := []string{}
	if o.Copy {
		args = append(args, constants.CopyKeyword)
	}
	if o.Replace {
		args = append(args, constants.ReplaceKeyword)
	}
	if o.Username != "" && o.Password == "" {
		return nil, fmt.Errorf("username requires a password")
	}
	if o.Username != "" && o.Password != "" {
		args = append(args, constants.Auth2Keyword, o.Username, o.Password)
	} else if o.Password != "" {
		args = append(args, constants.AuthKeyword, o.Password)
	}
	return args, nil
}
