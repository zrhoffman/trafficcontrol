#!/bin/sh -l
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

set -ex;

cd infrastructure/cdn-in-a-box;
tests_service='tr-integration-test';
other_services='dns edge enroller mid-01 mid-02 origin static trafficmonitor trafficops trafficrouter trafficvault';
docker_compose='docker-compose -f ./docker-compose.yml -f ./docker-compose.traffic-router-test.yml';
$docker_compose up -d $tests_service $other_services;

echo "Waiting for the ${tests_service} container to exit...";
if ! timeout 30m $docker_compose logs -f $tests_service; then
	echo "Traffic Router Integration and External tests did not complete within 30m minutes - exiting" >&2;
	exit_code=1;
	store_ciab_logs;
elif exit_code="$(docker inspect --format='{{.State.ExitCode}}' "$($docker_compose ps -q $tests_service)")"; [ "$exit_code" -ne 0 ]; then
	echo 'Traffic Router Integration Tests container exited with an error' >&2;
fi;

$docker_compose kill;
exit "$exit_code";
