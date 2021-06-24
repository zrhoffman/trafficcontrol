/*
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
package com.comcast.cdn.traffic_control.traffic_router.core.loc

import com.comcast.cdn.traffic_control.traffic_router.core.loc.AnonymousIpConfigUpdater
import org.apache.log4j.Logger
import java.io.File
import java.io.IOException

class AnonymousIpConfigUpdater : AbstractServiceUpdater() {
    @Throws(IOException::class)
    /*
     * Loads the anonymous ip config file
     */  override fun loadDatabase(): Boolean {
        AnonymousIpConfigUpdater.Companion.LOGGER.debug("AnonymousIpConfigUodater loading config")
        val existingDB = databasesDirectory.resolve(databaseName).toFile()
        return AnonymousIp.Companion.parseConfigFile(existingDB, false)
    }

    @Throws(IOException::class)
    /*
     * Verifies the anonymous ip config file
     */  override fun verifyDatabase(dbFile: File?): Boolean {
        AnonymousIpConfigUpdater.Companion.LOGGER.debug("AnonymousIpConfigUpdater verifying config")
        return AnonymousIp.Companion.parseConfigFile(dbFile, true)
    }

    companion object {
        private val LOGGER = Logger.getLogger(AnonymousIpConfigUpdater::class.java)
    }

    init {
        AnonymousIpConfigUpdater.Companion.LOGGER.debug("init...")
        sourceCompressed = false
        tmpPrefix = "anonymousip"
        tmpSuffix = ".json"
    }
}