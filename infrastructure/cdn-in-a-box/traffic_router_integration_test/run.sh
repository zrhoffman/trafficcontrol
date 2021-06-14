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
set -o errexit -o nounset

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

# Wait for the initial data load to be copied
until [[ -f "$ENROLLER_DIR/initial-load-done" ]] ; do
	echo "Waiting for enroller initial data load to complete...."
	sleep 2
	sync
done

# Wait for traffic monitor
until nc $TM_FQDN $TM_PORT </dev/null >/dev/null 2>&1; do
  echo "Waiting for Traffic Monitor to start..."
  sleep 3
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
cat <<'ROOT_TESTS_THINGY' > /etc/pki/ca-trust/source/anchors/root-tests-thingy.crt
-----BEGIN CERTIFICATE-----
MIIFwDCCA6igAwIBAgIJAItYgKMbCyfeMA0GCSqGSIb3DQEBCwUAMG0xCzAJBgNV
BAYTAlVTMREwDwYDVQQIDAhDb2xvcmFkbzEPMA0GA1UEBwwGRGVudmVyMRAwDgYD
VQQKDAdDb21jYXN0MQ4wDAYDVQQLDAVJUENETjEYMBYGA1UEAwwPVGVzdGluZyBS
b290IENBMB4XDTE2MDkyMzIxMTQzN1oXDTM4MDgxOTIxMTQzN1owbTELMAkGA1UE
BhMCVVMxETAPBgNVBAgMCENvbG9yYWRvMQ8wDQYDVQQHDAZEZW52ZXIxEDAOBgNV
BAoMB0NvbWNhc3QxDjAMBgNVBAsMBUlQQ0ROMRgwFgYDVQQDDA9UZXN0aW5nIFJv
b3QgQ0EwggIiMA0GCSqGSIb3DQEBAQUAA4ICDwAwggIKAoICAQDZDKwW4RapefC5
3qJB/mGG0Cv0pRBfyQwKbdSFSqFKq7yeG7mtT7WZgmvL2Z6vbTS4dVLM4iuZ7NhK
EWyZgYdOL1U/ZLHLB2H0oBJunOVVKGJhYIg78T3us0gYFJLSOJLpMx3buBe0YAFB
fqgc2iqULv87gVoWCWRVA0h8Dy33ktV5DX6TntkoVXVqyU7zwLwNP1RtrrNFTXRP
VlNO+eFvfFbYU7PVP8PigLTYOjkj+7gZWEUCKrkzey4LCnCco/xu1NY4gxVf0tfW
9UX9hdVFyZUmA2P/xTgmxeYtoTL7Y4r1YLRyvy8DO63Yij8IdCiChwxQm24Avj8l
MzL2wsh0MkojMjYf/x81YdzmvjCiLzTH+qnJmiEWHOvnyumhtWIS9AIuhLG6E2kI
I34lKF870JAF+16NgbFEE1AiVrHB/iglBraOfOCyv0CipLld7DqNKFQYnvdTr6/i
B0FzzdHtZf1fD2k/4xm65ktLDu6SKMpa35R/QGU1CH53CCF5F/FmAFIfxIiYlXOU
zQBj38fuIi64833TOVAyaSGkipNyG0/XJHt/wbAqia96fs8c+CbDdmo1v2t6UtQA
jnV9xbm1kOg27PvEpioFezO0YmaLg22Chn4AXJCoonqUBUgAOeEQ/Lvk0K6nay2D
e/XB9mdIF8FrPdz6SlzUGHdG2NSzqwIDAQABo2MwYTAdBgNVHQ4EFgQUpqb5QTnq
8+Ac7IV0GCRro8kg1W0wHwYDVR0jBBgwFoAUpqb5QTnq8+Ac7IV0GCRro8kg1W0w
DwYDVR0TAQH/BAUwAwEB/zAOBgNVHQ8BAf8EBAMCAYYwDQYJKoZIhvcNAQELBQAD
ggIBABbnpHxDpm9v9Gjo3wcQ93eh9Uhm/8Ne9TfA+Ijs0blTlylQmnLO1cfCgyUT
A767QAeadUIlaZdx2U9AL7nN877k/1LQrAK+XmSsfdvHwZxyfkdphmSP3IUaMCoe
dSQmJfZ1CQxtcyrnuDhjI4gYv3Evxogx9eADKk8siLuMgDdtxqpW2zet+5uYd2Li
xUfvPaHSGBRoeBVoh+qv1ApLSZJXXQYfraqNhCcu3sZvTqPwKZCAYnZl2vPTTS9o
pNOCbMi78/7SI8aVi1+Nxch3VSiAZBb7heFcUbSD8DiyuAuaMltSCqr98RnOFcEB
D/s9jvQDYY9qLmnU9/iBJ6vH66mxMwU67wThYdhVdvIJAiKi/tuFN+hLkcQw+Muz
OeOioBZKgNkdbhWTWG20Os7FSU/jRL2n5/pUcGMIa3XgE5XQyusyWFmgCBNwrjz6
H6w7g5IbkPghre0hDeSG/nv/6Hjw18ao966jSs6KhBjjDfiW+Ky9yhjatwv7m0XY
m3cs3WqRuTDdF2Mofswu6nHXubTp+JTPhcCLRT3uy/1ohB6TKCcc/ssjcPEcKNc1
Jfeqee2m48Yus2k35jnuvcfdSFqol8K1IJ6Uv1TmLW1dhBNaS9i3CLAuzkZqd9q4
d6Z+6TAIGHGp5PQVy+A5NpXEjGhpvi9W0FT3FvZMzQBvE/Bc
-----END CERTIFICATE-----
ROOT_TESTS_THINGY
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

set -o allexport
BUILD_LOCK=build_lock
BUILD_NUMBER=build_number
RHEL_VERSION=rhel_version
STARTUP_SCRIPT_DIR=/startup-dir
STARTUP_SCRIPT_LOC=../core/src/main/lib/systemd/system
TOMCAT_RELEASE=tomcat_release
TOMCAT_VERSION=tomcat_version
set +o allexport

trap 'mv core/target/surefire-reports/* core/target/failsafe-reports/* /junit' EXIT
trap - ERR
"${mvn_command[@]}" verify \
	-Djava.library.path=/usr/share/java:/usr/lib64 -DfailIfNoTests=false -DoutputDirectory=/junit 2>&1
