#!/usr/bin/env bash
set -euo pipefail
root=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)
dir=${1:-${DDD_SERVER_SECRETS_DIR:-$root/.runtime/ddd-server}}
umask 077
mkdir -p -- "$dir"

for name in bootstrap-password audit-service-key; do
    file="$dir/$name"
    if [[ ! -e "$file" ]]; then
        (set -o noclobber; openssl rand -hex 32 | tr -d '\r\n' > "$file")
    fi
    [[ -s "$file" ]] || { printf 'Empty secret file: %s\n' "$file" >&2; exit 1; }
done

private="$dir/jwt-private.pem"
public="$dir/jwt-public.pem"
if [[ ! -e "$private" && ! -e "$public" ]]; then
    (set -o noclobber; openssl genpkey -quiet -algorithm RSA -pkeyopt rsa_keygen_bits:3072 > "$private")
    (set -o noclobber; openssl pkey -in "$private" -pubout > "$public")
fi
if [[ ! -s "$private" || ! -s "$public" ]]; then
    printf 'Incomplete JWT key pair in %s. Restore the missing key.\n' "$dir" >&2
    exit 1
fi
if [[ "$(openssl pkey -in "$private" -pubout)" != "$(openssl pkey -pubin -in "$public" -pubout)" ]]; then
    printf 'JWT key pair does not match in %s.\n' "$dir" >&2
    exit 1
fi
printf 'Server secrets ready in %s\n' "$dir"
