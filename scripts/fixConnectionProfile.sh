#!/usr/bin/env bash

set -e

BASEDIR=$(pwd)
cd $BASEDIR

cp connectionProfile.yml cpAux.yml 
cat $(yq eval ".["$ref"]" connectionProfile.yml) > connectionProfile.yml
sed -i'' 's/\.\.\///g' connectionProfile.yml
