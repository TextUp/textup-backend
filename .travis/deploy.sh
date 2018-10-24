#!/bin/bash
#
# Deploy Grails app

set -e

REMOTE_USER=$1
REMOTE_HOST=$2
REMOTE_KEY=$3
WAR_DIRECTORY=$4
WAR_NAME=$5

REMOTE_SCRIPT=".travis/remote/deploy.sh"

chmod +x "$REMOTE_SCRIPT"

scp -v -oStrictHostKeyChecking=no -i "${REMOTE_KEY}" "${WAR_DIRECTORY}/${WAR_NAME}" "${REMOTE_USER}@${REMOTE_HOST}":"~"
ssh -v -oStrictHostKeyChecking=no -i "${REMOTE_KEY}" "${REMOTE_USER}@${REMOTE_HOST}" "bash -s" -- < "$REMOTE_SCRIPT" "${WAR_NAME}"
