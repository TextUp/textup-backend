#!/bin/bash
#
# Deploy Grails app

user=$1
host=$2
identity=$3
war_directory=$4
war_name=$5
remote_script=$6

scp -oStrictHostKeyChecking=no -i ${identity} ${war_directory}/${war_name} ${user}@${host}:~
ssh -oStrictHostKeyChecking=no -i ${identity} ${user}@${host} 'bash -s' -- < ${remote_script} ${war_name}
