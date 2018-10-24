#!/bin/bash

set -e

REMOTE_USER=$1
REMOTE_HOST=$2
REMOTE_KEY=$3
EXEC_USERNAME=$4
FFMPEG_DIRECTORY=$5
FFMPEG_COMMAND=$6

REMOTE_SCRIPT=".travis/remote/set-up-ffmpeg.sh"

chmod +x "$REMOTE_SCRIPT"

scp -v -oStrictHostKeyChecking=no -i "${REMOTE_KEY}" vendor/ffmpeg-4.0.2/linux/ffmpeg "${REMOTE_USER}@${REMOTE_HOST}":~
ssh -v -oStrictHostKeyChecking=no -i "${REMOTE_KEY}" "${REMOTE_USER}@${REMOTE_HOST}" "bash -s" -- < "$REMOTE_SCRIPT" "~/ffmpeg" "$EXEC_USERNAME" "$FFMPEG_DIRECTORY" "$FFMPEG_COMMAND"
