#!/usr/bin/env bash
# Starts Vault in dev mode for local development.
# The root token is "dev-root" — export VAULT_TOKEN=dev-root before running the app.

set -euo pipefail

vault server -dev -dev-root-token-id=dev-root -dev-listen-address=127.0.0.1:8200
