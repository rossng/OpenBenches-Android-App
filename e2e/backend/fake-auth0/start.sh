#!/bin/sh
# Makes a throwaway CA and a TLS certificate for this service's hostname, shared with the
# website container through /certs so it trusts https://$HOSTNAME_FOR_TLS/. Then starts the server.
set -e
mkdir -p /certs
cd /certs
if [ ! -f ca.crt ]; then
  openssl req -x509 -newkey rsa:2048 -nodes -days 3650 -subj "/CN=OpenBenches local test CA" \
    -keyout ca.key -out ca.crt 2>/dev/null
fi
openssl req -newkey rsa:2048 -nodes -subj "/CN=$HOSTNAME_FOR_TLS" -keyout server.key -out server.csr 2>/dev/null
printf "subjectAltName=DNS:%s\n" "$HOSTNAME_FOR_TLS" > san.ext
openssl x509 -req -in server.csr -CA ca.crt -CAkey ca.key -CAcreateserial -days 825 -extfile san.ext -out server.crt 2>/dev/null
# The token-signing key, with a certificate for it: Auth0's JWKS carries one (x5c), and the
# website's Auth0 SDK only accepts keys that have it.
if [ ! -f signing.key ]; then
  openssl req -x509 -newkey rsa:2048 -nodes -days 3650 -subj "/CN=$HOSTNAME_FOR_TLS signing" \
    -keyout signing.key -out signing.crt 2>/dev/null
fi
exec node /app/server.js
