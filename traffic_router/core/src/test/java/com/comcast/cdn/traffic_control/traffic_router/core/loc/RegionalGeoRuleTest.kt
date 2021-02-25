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
package com.comcast.cdn.traffic_control.traffic_router.core.loc

import kotlin.Throws
import java.lang.Exception
import com.comcast.cdn.traffic_control.traffic_router.core.ds.DeliveryService
import com.comcast.cdn.traffic_control.traffic_router.core.request.HTTPRequest
import com.comcast.cdn.traffic_control.traffic_router.core.ds.SteeringRegistry
import com.comcast.cdn.traffic_control.traffic_router.core.ds.SteeringTarget
import org.powermock.core.classloader.annotations.PrepareForTest
import org.junit.runner.RunWith
import org.powermock.modules.junit4.PowerMockRunner
import com.comcast.cdn.traffic_control.traffic_router.core.ds.DeliveryServiceMatcher
import com.comcast.cdn.traffic_control.traffic_router.geolocation.Geolocation
import com.comcast.cdn.traffic_control.traffic_router.core.ds.SteeringResult
import com.comcast.cdn.traffic_control.traffic_router.core.ds.SteeringGeolocationComparator
import com.comcast.cdn.traffic_control.traffic_router.shared.ZoneTestRecords
import org.xbill.DNS.RRset
import com.comcast.cdn.traffic_control.traffic_router.core.dns.RRSetsBuilder
import java.util.concurrent.ExecutorService
import java.util.concurrent.LinkedBlockingQueue
import com.comcast.cdn.traffic_control.traffic_router.core.dns.protocol.TCP
import java.net.InetAddress
import java.io.ByteArrayInputStream
import java.net.ServerSocket
import java.util.concurrent.BlockingQueue
import java.lang.Runnable
import com.comcast.cdn.traffic_control.traffic_router.core.dns.protocol.TCP.TCPSocketHandler
import org.xbill.DNS.DClass
import com.comcast.cdn.traffic_control.traffic_router.core.dns.DNSAccessRecord
import org.xbill.DNS.Rcode
import java.lang.RuntimeException
import org.powermock.api.mockito.PowerMockito
import java.net.DatagramSocket
import com.comcast.cdn.traffic_control.traffic_router.core.dns.protocol.UDP
import org.xbill.DNS.OPTRecord
import java.util.concurrent.atomic.AtomicInteger
import com.comcast.cdn.traffic_control.traffic_router.core.dns.protocol.UDP.UDPPacketHandler
import com.comcast.cdn.traffic_control.traffic_router.core.dns.protocol.AbstractProtocolTest.FakeAbstractProtocol
import com.comcast.cdn.traffic_control.traffic_router.core.dns.DNSAccessEventBuilder
import java.lang.System
import com.comcast.cdn.traffic_control.traffic_router.core.dns.protocol.AbstractProtocolTest
import java.net.Inet4Address
import org.xbill.DNS.ARecord
import org.xbill.DNS.WireParseException
import com.comcast.cdn.traffic_control.traffic_router.core.router.TrafficRouterManager
import com.comcast.cdn.traffic_control.traffic_router.core.router.TrafficRouter
import com.comcast.cdn.traffic_control.traffic_router.core.edge.CacheRegister
import org.xbill.DNS.NSRecord
import org.xbill.DNS.SOARecord
import org.xbill.DNS.ClientSubnetOption
import com.comcast.cdn.traffic_control.traffic_router.core.util.JsonUtils
import org.xbill.DNS.EDNSOption
import java.util.HashSet
import com.comcast.cdn.traffic_control.traffic_router.core.util.IntegrationTest
import java.util.HashMap
import com.comcast.cdn.traffic_control.traffic_router.core.dns.ZoneManagerTest
import com.google.common.net.InetAddresses
import org.xbill.DNS.TextParseException
import com.comcast.cdn.traffic_control.traffic_router.core.dns.ZoneManager
import com.comcast.cdn.traffic_control.traffic_router.core.edge.CacheLocation
import com.comcast.cdn.traffic_control.traffic_router.core.edge.Node.IPVersions
import com.google.common.cache.CacheStats
import java.nio.file.Paths
import com.comcast.cdn.traffic_control.traffic_router.core.TestBase
import com.comcast.cdn.traffic_control.traffic_router.core.dns.DNSException
import com.comcast.cdn.traffic_control.traffic_router.core.dns.NameServerMain
import com.comcast.cdn.traffic_control.traffic_router.core.dns.SignatureManager
import com.comcast.cdn.traffic_control.traffic_router.core.util.TrafficOpsUtils
import com.comcast.cdn.traffic_control.traffic_router.core.router.StatTracker
import org.xbill.DNS.SetResponse
import com.comcast.cdn.traffic_control.traffic_router.core.router.StatTracker.Track.ResultType
import org.xbill.DNS.NSECRecord
import org.xbill.DNS.RRSIGRecord
import com.comcast.cdn.traffic_control.traffic_router.core.router.StatTracker.Track.ResultDetails
import com.comcast.cdn.traffic_control.traffic_router.core.loc.GeolocationDatabaseUpdater
import com.comcast.cdn.traffic_control.traffic_router.core.loc.MaxmindGeolocationService
import com.comcast.cdn.traffic_control.traffic_router.core.loc.GeoTest
import com.comcast.cdn.traffic_control.traffic_router.core.loc.AnonymousIp
import com.comcast.cdn.traffic_control.traffic_router.core.loc.AnonymousIpDatabaseService
import com.comcast.cdn.traffic_control.traffic_router.core.loc.AnonymousIpWhitelist
import java.io.IOException
import com.comcast.cdn.traffic_control.traffic_router.core.loc.NetworkNode
import com.comcast.cdn.traffic_control.traffic_router.core.loc.NetworkNodeTest
import com.comcast.cdn.traffic_control.traffic_router.core.loc.NetworkNodeException
import com.comcast.cdn.traffic_control.traffic_router.core.loc.RegionalGeo
import com.comcast.cdn.traffic_control.traffic_router.core.loc.RegionalGeoResult
import com.comcast.cdn.traffic_control.traffic_router.core.loc.RegionalGeoResult.RegionalGeoResultType
import com.comcast.cdn.traffic_control.traffic_router.geolocation.GeolocationException
import java.net.MalformedURLException
import com.comcast.cdn.traffic_control.traffic_router.core.router.HTTPRouteResult
import com.comcast.cdn.traffic_control.traffic_router.core.edge.Cache.DeliveryServiceReference
import com.comcast.cdn.traffic_control.traffic_router.core.edge.CacheLocation.LocalizationMethod
import com.comcast.cdn.traffic_control.traffic_router.core.loc.MaxmindGeoIP2Test
import com.comcast.cdn.traffic_control.traffic_router.core.loc.RegionalGeoRule.PostalsType
import com.comcast.cdn.traffic_control.traffic_router.core.loc.RegionalGeoRule
import com.comcast.cdn.traffic_control.traffic_router.core.loc.NetworkNode.SuperNode
import com.comcast.cdn.traffic_control.traffic_router.core.loc.RegionalGeoCoordinateRange
import com.comcast.cdn.traffic_control.traffic_router.core.loc.Federation
import com.comcast.cdn.traffic_control.traffic_router.core.util.CidrAddress
import com.comcast.cdn.traffic_control.traffic_router.core.util.ComparableTreeSet
import com.comcast.cdn.traffic_control.traffic_router.core.loc.FederationMapping
import com.comcast.cdn.traffic_control.traffic_router.core.loc.FederationRegistry
import com.comcast.cdn.traffic_control.traffic_router.core.edge.InetRecord
import com.comcast.cdn.traffic_control.traffic_router.core.loc.FederationsBuilder
import com.comcast.cdn.traffic_control.traffic_router.core.util.JsonUtilsException
import com.comcast.cdn.traffic_control.traffic_router.core.loc.AbstractServiceUpdater
import java.nio.file.Path
import com.comcast.cdn.traffic_control.traffic_router.core.loc.AbstractServiceUpdaterTest.Updater
import com.comcast.cdn.traffic_control.traffic_router.core.loc.FederationMappingBuilder
import com.maxmind.geoip2.DatabaseReader
import com.maxmind.geoip2.model.CityResponse
import com.comcast.cdn.traffic_control.traffic_router.core.loc.AnonymousIpDatabaseServiceTest
import com.maxmind.geoip2.model.AnonymousIpResponse
import com.maxmind.geoip2.exception.GeoIp2Exception
import java.util.TreeSet
import com.comcast.cdn.traffic_control.traffic_router.core.http.HTTPAccessEventBuilder
import com.comcast.cdn.traffic_control.traffic_router.core.http.HTTPAccessRecord
import java.lang.StringBuffer
import com.comcast.cdn.traffic_control.traffic_router.core.util.Fetcher
import java.io.InputStreamReader
import org.powermock.core.classloader.annotations.PowerMockIgnore
import java.io.BufferedReader
import com.comcast.cdn.traffic_control.traffic_router.core.loc.FederationsWatcher
import com.comcast.cdn.traffic_control.traffic_router.core.ds.SteeringWatcher
import java.lang.InterruptedException
import com.comcast.cdn.traffic_control.traffic_router.core.util.AbstractResourceWatcherTest
import com.comcast.cdn.traffic_control.traffic_router.core.util.ComparableStringByLength
import com.comcast.cdn.traffic_control.traffic_router.core.config.ConfigHandler
import java.lang.Void
import com.comcast.cdn.traffic_control.traffic_router.shared.CertificateData
import com.comcast.cdn.traffic_control.traffic_router.core.config.CertificateChecker
import com.comcast.cdn.traffic_control.traffic_router.core.hash.ConsistentHasher
import com.comcast.cdn.traffic_control.traffic_router.core.ds.Dispersion
import com.comcast.cdn.traffic_control.traffic_router.core.router.DNSRouteResult
import com.comcast.cdn.traffic_control.traffic_router.core.request.DNSRequest
import com.comcast.cdn.traffic_control.traffic_router.core.loc.NetworkUpdater
import com.comcast.cdn.traffic_control.traffic_router.core.router.StatelessTrafficRouterTest
import com.comcast.cdn.traffic_control.traffic_router.core.router.LocationComparator
import com.comcast.cdn.traffic_control.traffic_router.secure.Pkcs1
import java.security.spec.KeySpec
import java.security.spec.PKCS8EncodedKeySpec
import com.comcast.cdn.traffic_control.traffic_router.core.secure.CertificatesClient
import com.comcast.cdn.traffic_control.traffic_router.core.hash.NumberSearcher
import com.comcast.cdn.traffic_control.traffic_router.core.hash.DefaultHashable
import com.comcast.cdn.traffic_control.traffic_router.core.hash.MD5HashFunction
import com.comcast.cdn.traffic_control.traffic_router.core.hash.Hashable
import com.comcast.cdn.traffic_control.traffic_router.core.util.ExternalTest
import org.apache.http.impl.client.CloseableHttpClient
import org.apache.catalina.LifecycleException
import org.apache.http.impl.client.HttpClientBuilder
import org.apache.http.client.methods.HttpGet
import org.apache.http.client.methods.CloseableHttpResponse
import org.apache.http.util.EntityUtils
import org.junit.runners.MethodSorters
import java.security.KeyStore
import com.comcast.cdn.traffic_control.traffic_router.core.external.RouterTest.ClientSslSocketFactory
import com.comcast.cdn.traffic_control.traffic_router.core.external.RouterTest.TestHostnameVerifier
import org.xbill.DNS.SimpleResolver
import javax.net.ssl.SSLHandshakeException
import org.apache.http.client.methods.HttpPost
import org.apache.http.client.methods.HttpHead
import org.apache.http.conn.ssl.SSLConnectionSocketFactory
import org.apache.http.conn.ssl.TrustSelfSignedStrategy
import javax.net.ssl.SNIHostName
import javax.net.ssl.SNIServerName
import javax.net.ssl.SSLParameters
import javax.net.ssl.SSLSession
import org.hamcrest.number.IsCloseTo
import com.comcast.cdn.traffic_control.traffic_router.core.http.RouterFilter
import java.net.InetSocketAddress
import org.junit.runners.Suite
import org.junit.runners.Suite.SuiteClasses
import com.comcast.cdn.traffic_control.traffic_router.core.external.SteeringTest
import com.comcast.cdn.traffic_control.traffic_router.core.external.ConsistentHashTest
import com.comcast.cdn.traffic_control.traffic_router.core.external.DeliveryServicesTest
import com.comcast.cdn.traffic_control.traffic_router.core.external.LocationsTest
import com.comcast.cdn.traffic_control.traffic_router.core.external.RouterTest
import com.comcast.cdn.traffic_control.traffic_router.core.external.StatsTest
import com.comcast.cdn.traffic_control.traffic_router.core.external.ZonesTest
import com.comcast.cdn.traffic_control.traffic_router.core.CatalinaTrafficRouter
import com.comcast.cdn.traffic_control.traffic_router.core.external.HttpDataServer
import com.comcast.cdn.traffic_control.traffic_router.core.external.ExternalTestSuite
import org.apache.log4j.ConsoleAppender
import org.apache.log4j.PatternLayout
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes
import java.nio.file.FileVisitResult
import org.hamcrest.number.OrderingComparison
import javax.management.MBeanServer
import javax.management.ObjectName
import com.comcast.cdn.traffic_control.traffic_router.shared.DeliveryServiceCertificatesMBean
import com.comcast.cdn.traffic_control.traffic_router.shared.DeliveryServiceCertificates
import org.springframework.context.support.FileSystemXmlApplicationContext
import kotlin.jvm.JvmStatic
import org.apache.catalina.startup.Catalina
import org.apache.catalina.core.StandardService
import java.util.stream.Collectors
import org.apache.catalina.core.StandardHost
import org.apache.catalina.core.StandardContext
import org.hamcrest.MatcherAssert
import org.hamcrest.Matchers
import org.junit.*
import java.util.ArrayList
import java.util.regex.Pattern

class RegionalGeoRuleTest {
    @Test
    @Throws(Exception::class)
    fun testIsAllowedCoordinateRanges() {
        val urlRegex = ".*abc.m3u8"
        val ruleType = PostalsType.INCLUDE
        val postals: Set<String> = HashSet()
        val whiteList: NetworkNode = SuperNode()
        val alternateUrl = "/alternate.m3u8"
        val coordinateRanges = ArrayList<RegionalGeoCoordinateRange>()
        val coordinateRange = RegionalGeoCoordinateRange()
        val coordinateRange2 = RegionalGeoCoordinateRange()
        coordinateRange.minLat = 10.0
        coordinateRange.minLon = 165.0
        coordinateRange.maxLat = 22.0
        coordinateRange.maxLon = 179.0
        coordinateRanges.add(coordinateRange)
        coordinateRange2.minLat = 17.0
        coordinateRange2.minLon = -20.0
        coordinateRange2.maxLat = 25.0
        coordinateRange2.maxLon = 19.0
        coordinateRanges.add(coordinateRange2)
        val urlRegexPattern = Pattern.compile(urlRegex, Pattern.CASE_INSENSITIVE)
        val urlRule = RegionalGeoRule(
            null,
            urlRegex, urlRegexPattern,
            ruleType, postals,
            whiteList, alternateUrl, coordinateRanges
        )
        var allowed: Boolean
        allowed = urlRule.isAllowedCoordinates(11.0, 170.0)
        MatcherAssert.assertThat(allowed, Matchers.equalTo(true))
        allowed = urlRule.isAllowedCoordinates(13.0, 162.0)
        MatcherAssert.assertThat(allowed, Matchers.equalTo(false))
        allowed = urlRule.isAllowedCoordinates(23.0, 22.0)
        MatcherAssert.assertThat(allowed, Matchers.equalTo(false))
        allowed = urlRule.isAllowedCoordinates(23.0, -12.0)
        MatcherAssert.assertThat(allowed, Matchers.equalTo(true))
        allowed = urlRule.isAllowedCoordinates(9.0, 21.0)
        MatcherAssert.assertThat(allowed, Matchers.equalTo(false))
    }

    @Test
    @Throws(Exception::class)
    fun testMatchesUrl() {
        val urlRegex = ".*abc.m3u8"
        val ruleType = PostalsType.INCLUDE
        val postals: Set<String> = HashSet()
        val whiteList: NetworkNode = SuperNode()
        val alternateUrl = "/alternate.m3u8"
        val urlRegexPattern = Pattern.compile(urlRegex, Pattern.CASE_INSENSITIVE)
        val urlRule = RegionalGeoRule(
            null,
            urlRegex, urlRegexPattern,
            ruleType, postals,
            whiteList, alternateUrl, null
        )
        var matches: Boolean
        var url = "http://example.com/abc.m3u8"
        matches = urlRule.matchesUrl(url)
        MatcherAssert.assertThat(matches, Matchers.equalTo(true))
        url = "http://example.com/AbC.m3u8"
        matches = urlRule.matchesUrl(url)
        MatcherAssert.assertThat(matches, Matchers.equalTo(true))
        url = "http://example.com/path/ABC.m3u8"
        matches = urlRule.matchesUrl(url)
        MatcherAssert.assertThat(matches, Matchers.equalTo(true))
        url = "http://example.com/cbaabc.m3u8"
        matches = urlRule.matchesUrl(url)
        MatcherAssert.assertThat(matches, Matchers.equalTo(true))
        url = "http://example.com/cba.m3u8"
        matches = urlRule.matchesUrl(url)
        MatcherAssert.assertThat(matches, Matchers.equalTo(false))
        url = "http://example.com/abcabc.m3u8"
        matches = urlRule.matchesUrl(url)
        MatcherAssert.assertThat(matches, Matchers.equalTo(true))
    }

    @Test
    @Throws(Exception::class)
    fun testIsAllowedPostalInclude() {
        val urlRegex = ".*abc.m3u8"
        val ruleType = PostalsType.INCLUDE
        val postals: MutableSet<String> = HashSet()
        postals.add("N6G")
        postals.add("N7G")
        val whiteList: NetworkNode = SuperNode()
        val alternateUrl = "/alternate.m3u8"
        val urlRegexPattern = Pattern.compile(urlRegex, Pattern.CASE_INSENSITIVE)
        val urlRule = RegionalGeoRule(
            null,
            urlRegex, urlRegexPattern,
            ruleType, postals,
            whiteList, alternateUrl, null
        )
        var allowed: Boolean
        allowed = urlRule.isAllowedPostal("N6G")
        MatcherAssert.assertThat(allowed, Matchers.equalTo(true))
        allowed = urlRule.isAllowedPostal("N7G")
        MatcherAssert.assertThat(allowed, Matchers.equalTo(true))
        allowed = urlRule.isAllowedPostal("N8G")
        MatcherAssert.assertThat(allowed, Matchers.equalTo(false))
    }

    @Test
    @Throws(Exception::class)
    fun testIsAllowedPostalExclude() {
        val urlRegex = ".*abc.m3u8"
        val ruleType = PostalsType.EXCLUDE
        val postals: MutableSet<String> = HashSet()
        postals.add("N6G")
        postals.add("N7G")
        val whiteList: NetworkNode = SuperNode()
        val alternateUrl = "/alternate.m3u8"
        val urlRegexPattern = Pattern.compile(urlRegex, Pattern.CASE_INSENSITIVE)
        val urlRule = RegionalGeoRule(
            null,
            urlRegex, urlRegexPattern,
            ruleType, postals,
            whiteList, alternateUrl, null
        )
        var allowed: Boolean
        allowed = urlRule.isAllowedPostal("N6G")
        MatcherAssert.assertThat(allowed, Matchers.equalTo(false))
        allowed = urlRule.isAllowedPostal("N7G")
        MatcherAssert.assertThat(allowed, Matchers.equalTo(false))
        allowed = urlRule.isAllowedPostal("N8G")
        MatcherAssert.assertThat(allowed, Matchers.equalTo(true))
    }

    @Test
    @Throws(Exception::class)
    fun testIsInWhiteList() {
        val urlRegex = ".*abc.m3u8"
        val ruleType = PostalsType.EXCLUDE
        val postals: Set<String> = HashSet()
        val whiteList = SuperNode()
        val location = RegionalGeoRule.WHITE_LIST_NODE_LOCATION
        whiteList.add(NetworkNode("10.74.50.0/24", location))
        whiteList.add(NetworkNode("10.74.0.0/16", location))
        whiteList.add(NetworkNode("192.168.250.1/32", location))
        whiteList.add(NetworkNode("128.128.50.3/32", location))
        whiteList.add(NetworkNode("128.128.50.3/22", location))
        whiteList.add6(NetworkNode("2001:0db8:0:f101::1/64", location))
        whiteList.add6(NetworkNode("2001:0db8:0:f101::1/48", location))
        val alternateUrl = "/alternate.m3u8"
        val urlRegexPattern = Pattern.compile(urlRegex, Pattern.CASE_INSENSITIVE)
        val urlRule = RegionalGeoRule(
            null,
            urlRegex, urlRegexPattern,
            ruleType, postals,
            whiteList, alternateUrl, null
        )
        var `in`: Boolean
        `in` = urlRule.isIpInWhiteList("10.74.50.12")
        MatcherAssert.assertThat(`in`, Matchers.equalTo(true))
        `in` = urlRule.isIpInWhiteList("10.75.51.12")
        MatcherAssert.assertThat(`in`, Matchers.equalTo(false))
        `in` = urlRule.isIpInWhiteList("10.74.51.1")
        MatcherAssert.assertThat(`in`, Matchers.equalTo(true))
        `in` = urlRule.isIpInWhiteList("10.74.50.255")
        MatcherAssert.assertThat(`in`, Matchers.equalTo(true))
        `in` = urlRule.isIpInWhiteList("192.168.250.1")
        MatcherAssert.assertThat(`in`, Matchers.equalTo(true))
        `in` = urlRule.isIpInWhiteList("128.128.50.3")
        MatcherAssert.assertThat(`in`, Matchers.equalTo(true))
        `in` = urlRule.isIpInWhiteList("128.128.50.7")
        MatcherAssert.assertThat(`in`, Matchers.equalTo(true))
        `in` = urlRule.isIpInWhiteList("128.128.2.1")
        MatcherAssert.assertThat(`in`, Matchers.equalTo(false))
        `in` = urlRule.isIpInWhiteList("2001:0db8:0:f101::2")
        MatcherAssert.assertThat(`in`, Matchers.equalTo(true))
        `in` = urlRule.isIpInWhiteList("2001:0db8:0:f102::1")
        MatcherAssert.assertThat(`in`, Matchers.equalTo(true))
        `in` = urlRule.isIpInWhiteList("2001:0db8:1:f101::3")
        MatcherAssert.assertThat(`in`, Matchers.equalTo(false))
    }

    @Test
    @Throws(Exception::class)
    fun testIsInWhiteListInvalidParam() {
        try {
            val urlRegex = ".*abc.m3u8"
            val ruleType = PostalsType.EXCLUDE
            val postals: Set<String> = HashSet()
            val whiteList = SuperNode()
            val location = RegionalGeoRule.WHITE_LIST_NODE_LOCATION
            whiteList.add(NetworkNode("10.256.0.0/10", location))
            //whiteList.add(new NetworkNode("a.b.d.0/10", location));
            val alternateUrl = "/alternate.m3u8"
            val urlRegexPattern = Pattern.compile(urlRegex, Pattern.CASE_INSENSITIVE)
            val urlRule = RegionalGeoRule(
                null,
                urlRegex, urlRegexPattern,
                ruleType, postals,
                whiteList, alternateUrl, null
            )
            var `in`: Boolean
            `in` = urlRule.isIpInWhiteList("10.74.50.12")
            MatcherAssert.assertThat(`in`, Matchers.equalTo(false))
            `in` = urlRule.isIpInWhiteList("10.74.51.12")
            MatcherAssert.assertThat(`in`, Matchers.equalTo(false))
            `in` = urlRule.isIpInWhiteList("1.1.50.1")
            MatcherAssert.assertThat(`in`, Matchers.equalTo(false))
            `in` = urlRule.isIpInWhiteList("2001:0db8:1:f101::3")
            MatcherAssert.assertThat(`in`, Matchers.equalTo(false))
        } catch (e: NetworkNodeException) {
        }
    }

    @Test
    @Throws(Exception::class)
    fun testIsInWhiteListGlobalMatch() {
        val urlRegex = ".*abc.m3u8"
        val ruleType = PostalsType.EXCLUDE
        val postals: Set<String> = HashSet()
        val whiteList = SuperNode()
        val location = RegionalGeoRule.WHITE_LIST_NODE_LOCATION
        whiteList.add(NetworkNode("0.0.0.0/0", location))
        val alternateUrl = "/alternate.m3u8"
        val urlRegexPattern = Pattern.compile(urlRegex, Pattern.CASE_INSENSITIVE)
        val urlRule = RegionalGeoRule(
            null,
            urlRegex, urlRegexPattern,
            ruleType, postals,
            whiteList, alternateUrl, null
        )
        var `in`: Boolean
        `in` = urlRule.isIpInWhiteList("10.74.50.12")
        MatcherAssert.assertThat(`in`, Matchers.equalTo(true))
        `in` = urlRule.isIpInWhiteList("10.74.51.12")
        MatcherAssert.assertThat(`in`, Matchers.equalTo(true))
        `in` = urlRule.isIpInWhiteList("1.1.50.1")
        MatcherAssert.assertThat(`in`, Matchers.equalTo(true))
        `in` = urlRule.isIpInWhiteList("222.254.254.254")
        MatcherAssert.assertThat(`in`, Matchers.equalTo(true))
        `in` = urlRule.isIpInWhiteList("2001:0db8:1:f101::3")
        MatcherAssert.assertThat(`in`, Matchers.equalTo(false))
    }
}