#!/usr/bin/env bash
# Licensed to the Apache Software Foundation (ASF) under one
# or more contributor license agreements.  See the NOTICE file
# distributed with this work for additional information
# regarding copyright ownership.  The ASF licenses this file
# to you under the Apache License, Version 2.0 (the
# "License"); you may not use this file except in compliance
# with the License.  You may obtain a copy of the License at
#
#   http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing,
# software distributed under the License is distributed on an
# "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
# KIND, either express or implied.  See the License for the
# specific language governing permissions and limitations
# under the License.

trap 'echo "Error on line ${LINENO} of ${0}"; exit 1' ERR
set -o xtrace -o errexit -o nounset

set-dns.sh
insert-self-into-dns.sh
source /to-access.sh

# Wait on SSL certificate generation
until [[ -f "$X509_CA_ENV_FILE" ]]
do
  echo "Waiting on Shared SSL certificate generation"
  sleep 3
done

# Source the CIAB-CA shared SSL environment
until [[ -v 'X509_GENERATION_COMPLETE' ]]
do
  echo "Waiting on X509 vars to be defined"
  sleep 1
  source "$X509_CA_ENV_FILE"
done

mvn_command=(mvn);
if [[ "$TR_TEST_DEBUG_ENABLE" == true ]]; then
		mvn_command+=(
		'-Dmaven.surefire.debug=-agentlib:jdwp=transport=dt_socket,server=n,suspend=n,address=host.docker.internal:8000 -DforkCount=0 -DreuseForks=false' # debug properties for surefire (unit) tests
		'-Dmaven.failsafe.debug=-agentlib:jdwp=transport=dt_socket,server=n,suspend=n,address=host.docker.internal:8000' # debug properties for failsafe (integration and external) tests
	)
fi;

# Trust the CIAB-CA at the System level
cp $X509_CA_CERT_FULL_CHAIN_FILE /etc/pki/ca-trust/source/anchors
update-ca-trust extract

tm_properties_file=traffic_monitor.properties
to_properties_file=traffic_ops.properties
(
cd core/src/test/conf
<<TM_PROPERTIES cat > "$tm_properties_file"
traffic_monitor.bootstrap.local = false
traffic_monitor.properties=$(realpath "$tm_properties_file")
traffic_monitor.bootstrap.hosts=${TM_FQDN}:${TM_PORT};
traffic_monitor.properties.reload.period=60000
TM_PROPERTIES

<<TO_PROPERTIES cat >> "$to_properties_file"
traffic_ops.username=${TO_ADMIN_USER}
traffic_ops.password=${TO_ADMIN_PASSWORD}
TO_PROPERTIES
)
sed -i "s|traffic_monitor\\.bootstrap\\.hosts|${TM_FQDN}:${TM_PORT}|g" core/src/main/webapp/WEB-INF/applicationContext.xml

trap 'mv core/target/surefire-reports/* core/target/failsafe-reports/* /junit' EXIT
trap - ERR
"${mvn_command[@]}" verify \
	-Djava.library.path=/usr/share/java:/usr/lib64 -DfailIfNoTests=false -DoutputDirectory=/junit 2>&1
