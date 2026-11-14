#!/usr/bin/env bash
# Never prints credentials; existing secrets and keys are immutable on rerun.
set -euo pipefail
source "$(dirname "$0")/full-env.sh"
umask 077
mkdir -p "$RUNTIME_DIR"
protect_runtime() {
 if [[ ${OS:-} == Windows_NT ]]; then
  # Directory inheritance flags are not valid effective permissions on existing files.
  # Grant the current owner explicitly on each file, never an empty file DACL.
  local winpath
  winpath=$(cygpath -w "$RUNTIME_DIR")
  MSYS_NO_PATHCONV=1 icacls.exe "$winpath" /inheritance:r /grant:r "${USERNAME}:(OI)(CI)F" > /dev/null
  for file in "$RUNTIME_DIR/"*; do
   [[ -f "$file" ]] || continue
   MSYS_NO_PATHCONV=1 icacls.exe "$(cygpath -w "$file")" /inheritance:r /grant:r "${USERNAME}:F" > /dev/null
  done
 else
  chmod 700 "$RUNTIME_DIR"
  for file in "$RUNTIME_DIR/"*; do [[ ! -f "$file" ]] || chmod 600 "$file"; done
 fi
}
protect_runtime
for name in mysql-root-password center-db-password course-db-password bootstrap-password audit-service-key; do
 [[ -s "$RUNTIME_DIR/$name" ]] || openssl rand -hex 32 | tr -d '\r\n' > "$RUNTIME_DIR/$name"
done
if [[ ! -f "$RUNTIME_DIR/jwt-private.pem" ]]; then
 [[ ! -f "$RUNTIME_DIR/jwt-public.pem" ]] || { echo 'Partial JWT pair; refusing replacement' >&2; exit 1; }
 openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:3072 -out "$RUNTIME_DIR/jwt-private.pem" 2>/dev/null
 openssl pkey -in "$RUNTIME_DIR/jwt-private.pem" -pubout -out "$RUNTIME_DIR/jwt-public.pem"
fi
if [[ ! -s "$RUNTIME_DIR/tls.crt" ]]; then
 tls_key="$RUNTIME_DIR/tls.key"; tls_cert="$RUNTIME_DIR/tls.crt"
 if [[ ${OS:-} == Windows_NT ]]; then tls_key=$(cygpath -w "$tls_key"); tls_cert=$(cygpath -w "$tls_cert"); fi
 MSYS_NO_PATHCONV=1 openssl req -x509 -newkey rsa:3072 -nodes -keyout "$tls_key" -out "$tls_cert" -days 30 -subj '/CN=localhost' -addext 'subjectAltName=DNS:localhost,DNS:identity.rbac1.local,IP:127.0.0.1' 2>/dev/null
fi
protect_runtime
echo 'Local secrets prepared. TLS certificate is self-signed, NOT publicly trusted.'
