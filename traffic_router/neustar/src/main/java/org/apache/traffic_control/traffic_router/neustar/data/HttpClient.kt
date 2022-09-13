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
package org.apache.traffic_control.traffic_router.neustar.data

import org.apache.http.client.methods.CloseableHttpResponse

class HttpClient {
    private val LOGGER: Logger? = LogManager.getLogger(HttpClient::class.java)
    private var httpClient: CloseableHttpClient? = null
    fun execute(request: HttpUriRequest?): CloseableHttpResponse? {
        return try {
            httpClient = HttpClientBuilder.create().build()
            httpClient.execute(request)
        } catch (e: IOException) {
            LOGGER.warn("Failed to execute http request " + request.getMethod() + " " + request.getURI() + ": " + e.getMessage())
            try {
                httpClient.close()
            } catch (e1: IOException) {
                LOGGER.warn("After exception, Failed to close Http Client " + e1.getMessage())
            }
            null
        }
    }

    fun close() {
        try {
            httpClient.close()
        } catch (e: IOException) {
            LOGGER.warn("Failed to close Http Client " + e.getMessage())
        }
    }
}