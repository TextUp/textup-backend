#!/bin/bash

set -e

PATH_TO_VENDOR_COMMAND=$1
EXEC_USERNAME=$2
FFMPEG_DIRECTORY=$3
FFMPEG_COMMAND=$4

# check to see if ffmpeg directory exists + create if does not exist
if [ ! -d "${FFMPEG_DIRECTORY}" ] && [ ! -L "${FFMPEG_DIRECTORY}" ]; then
    sudo mkdir -pv "${FFMPEG_DIRECTORY}"
fi

# copy the ffmpeg over every time, even if it already exists to make sure we have intended version
# mark the ffmpeg executable as executable
sudo cp -vf "${PATH_TO_VENDOR_COMMAND}" "${FFMPEG_DIRECTORY}/${FFMPEG_COMMAND}"
sudo chmod -vR +x "${FFMPEG_DIRECTORY}/${FFMPEG_COMMAND}"
sudo chown -vR "${EXEC_USERNAME}:${EXEC_USERNAME}" "${FFMPEG_DIRECTORY}"

# cleanup
rm -v "${PATH_TO_VENDOR_COMMAND}"
