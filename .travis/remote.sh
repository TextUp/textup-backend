#!/bin/bash
#
# This script is run remote on the EC2 server. Copy Grails app to correct directory
# and restart Tomcat7 server to load new version of the app

war_name=$1

cd /var/lib/tomcat7/webapps
rm ${war_name}.backup
mv ${war_name} ${war_name}.backup
mv ~/${war_name} .

sudo service tomcat7 stop
sudo rm -rf ROOT
sudo service tomcat7 start
