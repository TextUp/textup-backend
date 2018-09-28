#!/bin/bash
#
# This script is run remote on the EC2 server. Copy Grails app to correct directory
# and restart Tomcat7 server to load new version of the app

set -e

WAR_NAME=$1

cd /var/lib/tomcat7/webapps
rm -f "${WAR_NAME}.backup"
mv "${WAR_NAME}" "${WAR_NAME}.backup"
mv "~/${WAR_NAME}" .

sudo service tomcat7 stop
sudo rm -rf ROOT
sudo service tomcat7 start
