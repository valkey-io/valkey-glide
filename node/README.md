This package contains an internal package under the folder `rust-client`, which is just the Rust library and its auto-generated TS code. The external package, in this folder, contains all wrapping TS code and tests.

### First time install

Homebrew is the easiest way to install node, but you can choose any way you want to install it.
Npm should come as part as node. IMPORTANT - right now we support only npm major version 8.
The default install will be of version 9, so you'll need to downgrade - run `npm i -g npm@8`.

### Build

Run `npm run build-internal` to build the internal package and generates TS code. `npm run build-external` builds the external package without rebuilding the internal package.
Run `npm run build` runs a full build.

### Testing

Run `npm test` after building.
