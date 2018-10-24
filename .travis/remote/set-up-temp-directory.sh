#!/bin/bash

set -e

EXEC_USERNAME=$1
TEMP_DIRECTORY=$2

# check to see if ffmpeg directory exists + create if does not exist
if [ ! -d "${TEMP_DIRECTORY}" ] && [ ! -L "${TEMP_DIRECTORY}" ]; then
    sudo mkdir -pv "${TEMP_DIRECTORY}"
fi

sudo chmod -vR 660 "${TEMP_DIRECTORY}"
sudo chown -vR "${EXEC_USERNAME}" "${TEMP_DIRECTORY}"
