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

set -o errexit -o nounset -o xtrace
trap 'echo "Error on line ${LINENO} of ${0}"; exit 1' ERR

export DOCKER_BUILDKIT=1 COMPOSE_DOCKER_CLI_BUILD=1

pkg_command=(./pkg -v)
if [[ -n "$GITHUB_BASE_REF" ]]; then
	ref="$(git merge-base "${GITHUB_BASE_REF}" "${GITHUB_HEAD_REF}")...${GITHUB_HEAD_REF}"
else
	ref="$GITHUB_SHA"
fi
if ! git diff --name-only "$ref" --exit-code -- GO_VERSION; then
	pkg_command+=(-b)
fi

if [[ -z "${ATC_COMPONENT:-}" ]]; then
	echo 'Missing environment variable ATC_COMPONENT' >/dev/stderr
	exit 1
fi
ATC_COMPONENT+='_build'

"${pkg_command[@]}" "$ATC_COMPONENT"
