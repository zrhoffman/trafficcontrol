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
package org.apache.traffic_control.traffic_router.core.dns.keys

import org.apache.traffic_control.traffic_router.core.dns.RRSetsBuilder

import org.apache.traffic_control.traffic_router.shared.ZoneTestRecordsimport org.hamcrest.MatcherAssertimport org.hamcrest.Matchersimport org.junit.Beforeimport org.junit.Testimport org.xbill.DNS.RRsetimport org.xbill.DNS.Type
class RRSetsBuilderTest {
    @Before
    @Throws(Exception::class)
    fun before() {
        ZoneTestRecords.generateZoneRecords(false)
    }

    fun findRRSet(rRsets: MutableList<RRset?>?, name: String?, type: Int): RRset? {
        val option = rRsets.stream()
                .filter { rRset: RRset? -> name == rRset.getName().toString() && rRset.getType() == type }
                .findFirst()
        return if (option.isPresent) option.get() else null
    }

    @Test
    @Throws(Exception::class)
    fun itGroupsResourceRecordsAccordingToRfc4034() {
        val rRsets = RRSetsBuilder().build(ZoneTestRecords.records)
        MatcherAssert.assertThat(rRsets.size, Matchers.equalTo(9))
        MatcherAssert.assertThat(findRRSet(rRsets, "mirror.www.example.com.", Type.CNAME), Matchers.notNullValue())
        MatcherAssert.assertThat(findRRSet(rRsets, "ftp.example.com.", Type.AAAA), Matchers.notNullValue())
        MatcherAssert.assertThat(findRRSet(rRsets, "ftp.example.com.", Type.A), Matchers.notNullValue())
        MatcherAssert.assertThat(findRRSet(rRsets, "www.example.com.", Type.A), Matchers.notNullValue())
        MatcherAssert.assertThat(findRRSet(rRsets, "www.example.com.", Type.TXT), Matchers.notNullValue())
        MatcherAssert.assertThat(findRRSet(rRsets, "example.com.", Type.NS), Matchers.notNullValue())
        MatcherAssert.assertThat(findRRSet(rRsets, "mirror.ftp.example.com.", Type.CNAME), Matchers.notNullValue())
        MatcherAssert.assertThat(findRRSet(rRsets, "www.example.com.", Type.AAAA), Matchers.notNullValue())
        MatcherAssert.assertThat(findRRSet(rRsets, "example.com.", Type.SOA), Matchers.notNullValue())
    }
}