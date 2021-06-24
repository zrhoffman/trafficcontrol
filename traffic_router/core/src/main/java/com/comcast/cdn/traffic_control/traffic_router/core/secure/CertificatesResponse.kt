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
 */
package com.comcast.cdn.traffic_control.traffic_router.core.secure

import com.comcast.cdn.traffic_control.traffic_router.shared.CertificateData
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty

@JsonIgnoreProperties(ignoreUnknown = true)
class CertificatesResponse {
    @JsonProperty
    private var response: MutableList<CertificateData?>? = null
    fun getResponse(): MutableList<CertificateData?>? {
        return response
    }

    fun setResponse(response: MutableList<CertificateData?>?) {
        this.response = response
    }
}