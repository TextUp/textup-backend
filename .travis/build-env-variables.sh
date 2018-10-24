#!/bin/bash

set -e

REMOTE_USER=$1
REMOTE_HOST=$2
REMOTE_KEY=$3
PATH_TO_ENV_VARIABLES=$4

REMOTE_SCRIPT=".travis/remote/build-env-variables.sh"

chmod +x "$REMOTE_SCRIPT"

scp -v -oStrictHostKeyChecking=no -i "${REMOTE_KEY}" "$PATH_TO_ENV_VARIABLES" "${REMOTE_USER}@${REMOTE_HOST}":"~/setenv.sh"
ssh -v -oStrictHostKeyChecking=no -i "${REMOTE_KEY}" "${REMOTE_USER}@${REMOTE_HOST}" "bash -s" -- < "$REMOTE_SCRIPT" "~/setenv.sh"
