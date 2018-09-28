#!/bin/bash

set -e

PATH_TO_ENV_VARIABLES=$1

cd /usr/share/tomcat7/bin
sudo rm -f setenv.sh.backup
sudo mv setenv.sh setenv.sh.backup
sudo mv "$PATH_TO_ENV_VARIABLES" setenv.sh

sudo chmod -vR 755 setenv.sh
sudo chmod root:root setenv.sh
