#!/bin/bash
#
# Deploy Grails app

user=$1
host=$2
identity=$3
war_directory=$4
war_name=$5
remote_script=$6

scp -i ${identity} ${war_directory}/${war_name} ${user}@${host}:~
ssh -i ${identity} ${user}@${host} 'bash -s' -- < ${remote_script} ${war_name}
