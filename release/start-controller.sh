#!/bin/bash
#
#Copyright 2013 Intel Corporation, All Rights Reserved.
#
#Licensed under the Apache License, Version 2.0 (the "License");
#you may not use this file except in compliance with the License.
#You may obtain a copy of the License at
#
#   http://www.apache.org/licenses/LICENSE-2.0
#
#Unless required by applicable law or agreed to in writing, software
#distributed under the License is distributed on an "AS IS" BASIS,
#WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
#See the License for the specific language governing permissions and
#limitations under the License.
#

#-------------------------------
# COSBENCH CONTROLLER STARTER
#-------------------------------

# Stop before start.
bash stop-controller.sh
echo
echo "======================================================"
echo

# Start here.
SERVICE_NAME=controller
VERSION=`cat VERSION`

OSGI_BUNDLES="cosbench-log_${VERSION} cosbench-tomcat_${VERSION} cosbench-config_${VERSION} cosbench-core_${VERSION} cosbench-core-web_${VERSION} cosbench-controller_${VERSION} cosbench-controller-web_${VERSION}"

OSGI_CONSOLE_PORT=19089

sh cosbench-start.sh $SERVICE_NAME "$OSGI_BUNDLES" $OSGI_CONSOLE_PORT
