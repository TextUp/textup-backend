#!/bin/bash

set -e

PATH_TO_ENV_VARIABLES=$1

cd /usr/share/tomcat7/bin
[ -f setenv.sh.backup ] && sudo rm -f setenv.sh.backup
[ -f setenv.sh ] && sudo mv setenv.sh setenv.sh.backup
sudo mv "$PATH_TO_ENV_VARIABLES" setenv.sh

sudo chmod -vR 755 setenv.sh
sudo chown -vR root:root setenv.sh
