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

ARG RHEL_VERSION=8
FROM centos:${RHEL_VERSION}
ARG RHEL_VERSION=8

ARG DIR=github.com/apache/trafficcontrol

RUN if [[ "${RHEL_VERSION%%.*}" -eq 7 ]]; then \
        yum -y install dnf || exit 1; \
    fi

RUN set -o errexit;\
    dnf -y update;\
    dnf -y install \
        bind-utils \
        epel-release \
        iproute \
        java-1.8.0-openjdk-devel \
        java-1.8.0-openjdk-headless \
        maven \
        net-tools \
        rpm-build;\
    dnf -y install tomcat-native

COPY LICENSE /go/src/$DIR/
COPY traffic_router /go/src/$DIR/traffic_router
WORKDIR /go/src/$DIR/traffic_router

COPY /infrastructure/cdn-in-a-box/traffic_ops/to-access.sh /
COPY /infrastructure/cdn-in-a-box/traffic_router_integration_test/run.sh \
     /usr/local/sbin/

CMD run.sh
