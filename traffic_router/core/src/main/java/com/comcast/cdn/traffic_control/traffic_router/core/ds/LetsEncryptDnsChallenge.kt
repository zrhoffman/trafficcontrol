package com.comcast.cdn.traffic_control.traffic_router.core.ds

import com.fasterxml.jackson.annotation.JsonProperty
import java.util.Objects

/*
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */   class LetsEncryptDnsChallenge {
    @JsonProperty
    private var fqdn: String? = null

    @JsonProperty
    private var record: String? = null
    fun getFqdn(): String? {
        return fqdn
    }

    fun setFqdn(fqdn: String?) {
        this.fqdn = fqdn
    }

    fun getRecord(): String? {
        return record
    }

    fun setRecord(record: String?) {
        this.record = record
    }

    override fun equals(o: Any?): Boolean {
        if (this === o) {
            return true
        }
        if (o == null || javaClass != o.javaClass) {
            return false
        }
        val that = o as LetsEncryptDnsChallenge?
        return fqdn == that.fqdn && record == that.record
    }

    override fun hashCode(): Int {
        return Objects.hash(fqdn, record)
    }
}