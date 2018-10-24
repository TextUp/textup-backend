#!/bin/bash

set -e

REMOTE_USER=$1
REMOTE_HOST=$2
REMOTE_KEY=$3
EXEC_USERNAME=$4
TEMP_DIRECTORY=$5

REMOTE_SCRIPT=".travis/remote/set-up-temp-directory.sh"

chmod +x "$REMOTE_SCRIPT"
ssh -v -oStrictHostKeyChecking=no -i "${REMOTE_KEY}" "${REMOTE_USER}@${REMOTE_HOST}" "bash -s" -- < "$REMOTE_SCRIPT" "$EXEC_USERNAME" "$TEMP_DIRECTORY"
