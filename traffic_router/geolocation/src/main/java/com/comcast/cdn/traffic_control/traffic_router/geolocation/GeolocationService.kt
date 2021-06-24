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
package com.comcast.cdn.traffic_control.traffic_router.geolocation

import java.io.File
import java.io.IOException

interface GeolocationService {
    /**
     * Provides a geospatial location for a specified IP Address.
     *
     * @param ip
     * @return the location of the specified IP Address
     * @throws GeolocationException
     * if the IP Address cannot be located.
     */
    @Throws(GeolocationException::class)
    fun location(ip: String?): Geolocation?

    /**
     * Forces a reload of the geolocation database.
     *
     * @throws IOException
     */
    @Throws(IOException::class)
    fun reloadDatabase()

    /**
     * Verifies the specified database is valid.
     *
     * @param dbFile
     * the database file.
     * @throws IOException
     * if the database is not valid.
     */
    @Throws(IOException::class)
    fun verifyDatabase(dbFile: File?): Boolean

    /**
     * Exposes whether this GeolocationService has loaded
     *
     * @return whether this GeolocationService has loaded
     */
    fun isInitialized(): Boolean
    fun setDatabaseFile(databaseFile: File?)
}