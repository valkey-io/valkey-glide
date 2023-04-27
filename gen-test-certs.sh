#!/bin/bash

# Copied from https://github.com/redis/redis/blob/8c291b97b95f2e011977b522acf77ead23e26f55/utils/gen-test-certs.sh

# Generate some test certificates which are used by the regression test suite:
#
#   $FOLDER/tls/ca.{crt,key}          Self signed CA certificate.
#   $FOLDER/tls/redis.{crt,key}       A certificate with no key usage/policy restrictions.
#   $FOLDER/tls/client.{crt,key}      A certificate restricted for SSL client usage.
#   $FOLDER/tls/server.{crt,key}      A certificate restricted fro SSL server usage.
#   $FOLDER/tls/redis.dh              DH Params file.

KEY_BITS=2048

generate_cert() {
    local name=$1
    local cn="$2"
    local opts="$3"

    local keyfile=$FOLDER/tls/${name}.key
    local certfile=$FOLDER/tls/${name}.crt

    [ -f $keyfile ] || openssl genrsa -out $keyfile $KEY_BITS
    openssl req \
        -new -sha256 \
        -subj "/O=Redis Test/CN=$cn" \
        -key $keyfile | \
        openssl x509 \
            -req -sha256 \
            -CA $FOLDER/tls/ca.crt \
            -CAkey $FOLDER/tls/ca.key \
            -CAserial $FOLDER/tls/ca.txt \
            -CAcreateserial \
            -days 365 \
            $opts \
            -out $certfile
}

mkdir -p $FOLDER/tls
[ -f $FOLDER/tls/ca.key ] || openssl genrsa -out $FOLDER/tls/ca.key 4096
openssl req \
    -x509 -new -nodes -sha256 \
    -key $FOLDER/tls/ca.key \
    -days 3650 \
    -subj '/O=Redis Test/CN=Certificate Authority' \
    -out $FOLDER/tls/ca.crt

cat > $FOLDER/tls/openssl.cnf <<_END_
[ server_cert ]
keyUsage = digitalSignature, keyEncipherment
nsCertType = server
[ client_cert ]
keyUsage = digitalSignature, keyEncipherment
nsCertType = client
_END_

# generate_cert server "Server-only" "-extfile $FOLDER/tls/openssl.cnf -extensions server_cert"
# generate_cert client "Client-only" "-extfile $FOLDER/tls/openssl.cnf -extensions client_cert"
generate_cert redis "Generic-cert"

[ -f $FOLDER/tls/redis.dh ] || openssl dhparam -out $FOLDER/tls/redis.dh $KEY_BITS
