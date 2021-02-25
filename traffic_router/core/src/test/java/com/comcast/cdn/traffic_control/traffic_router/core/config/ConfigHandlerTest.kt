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
package com.comcast.cdn.traffic_control.traffic_router.core.config

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
import com.comcast.cdn.traffic_control.traffic_router.core.util.IntegrationTest
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
import com.comcast.cdn.traffic_control.traffic_router.core.edge.Cache
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
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.context.support.FileSystemXmlApplicationContext
import kotlin.jvm.JvmStatic
import org.apache.catalina.startup.Catalina
import org.apache.catalina.core.StandardService
import java.util.stream.Collectors
import org.apache.catalina.core.StandardHost
import org.apache.catalina.core.StandardContext
import org.hamcrest.MatcherAssert
import org.junit.*
import org.mockito.Matchers
import org.mockito.Mockito
import org.powermock.reflect.Whitebox
import java.util.*

class ConfigHandlerTest {
    private var handler: ConfigHandler? = null
    @Before
    @Throws(Exception::class)
    fun before() {
        handler = Mockito.mock(ConfigHandler::class.java)
    }

    @Test
    @Throws(Exception::class)
    fun itTestRelativeUrl() {
        val redirectUrl = "relative/url"
        val dsId = "relative-url"
        val urlType = arrayOf("")
        val typeUrl = arrayOf("")
        val dsMap: MutableMap<String, DeliveryService> = HashMap()
        val ds = Mockito.mock(DeliveryService::class.java)
        Mockito.`when`(ds.id).thenReturn(dsId)
        Mockito.`when`(ds.geoRedirectUrl).thenReturn(redirectUrl)
        Mockito.doAnswer { invocation ->
            val args = invocation.arguments
            typeUrl[0] = args[0] as String
            null
        }.`when`(ds).geoRedirectFile = Matchers.anyString()
        Mockito.doAnswer { invocation ->
            val args = invocation.arguments
            urlType[0] = args[0] as String
            null
        }.`when`(ds).geoRedirectUrlType = Matchers.anyString()
        dsMap[dsId] = ds
        val register = PowerMockito.mock(CacheRegister::class.java)
        Whitebox.invokeMethod<Any>(handler, "initGeoFailedRedirect", dsMap, register)
        MatcherAssert.assertThat(urlType[0], org.hamcrest.Matchers.equalTo("DS_URL"))
        MatcherAssert.assertThat(typeUrl[0], org.hamcrest.Matchers.equalTo(""))
    }

    @Test
    @Throws(Exception::class)
    fun itTestRelativeUrlNegative() {
        val redirectUrl = "://invalid"
        val dsId = "relative-url"
        val urlType = arrayOf("")
        val typeUrl = arrayOf("")
        val dsMap: MutableMap<String, DeliveryService> = HashMap()
        val ds = Mockito.mock(DeliveryService::class.java)
        Mockito.`when`(ds.id).thenReturn(dsId)
        Mockito.`when`(ds.geoRedirectUrl).thenReturn(redirectUrl)
        Mockito.doAnswer { invocation ->
            val args = invocation.arguments
            typeUrl[0] = args[0] as String
            null
        }.`when`(ds).geoRedirectFile = Matchers.anyString()
        Mockito.doAnswer { invocation ->
            val args = invocation.arguments
            urlType[0] = args[0] as String
            null
        }.`when`(ds).geoRedirectUrlType = Matchers.anyString()
        dsMap[dsId] = ds
        val register = PowerMockito.mock(CacheRegister::class.java)
        Whitebox.invokeMethod<Any>(handler, "initGeoFailedRedirect", dsMap, register)
        MatcherAssert.assertThat(urlType[0], org.hamcrest.Matchers.equalTo(""))
        MatcherAssert.assertThat(typeUrl[0], org.hamcrest.Matchers.equalTo(""))
    }

    @Test
    @Throws(Exception::class)
    fun itTestNoSuchDsUrl() {
        val path = "/ds/url"
        val redirectUrl = "http://test.com$path"
        val dsId = "relative-url"
        val urlType = arrayOf("")
        val typeUrl = arrayOf("")
        val dsMap: MutableMap<String, DeliveryService> = HashMap()
        val ds = Mockito.mock(DeliveryService::class.java)
        Mockito.`when`(ds.id).thenReturn(dsId)
        Mockito.`when`(ds.geoRedirectUrl).thenReturn(redirectUrl)
        Mockito.doAnswer { invocation ->
            val args = invocation.arguments
            typeUrl[0] = args[0] as String
            null
        }.`when`(ds).geoRedirectFile = Matchers.anyString()
        Mockito.doAnswer { invocation ->
            val args = invocation.arguments
            urlType[0] = args[0] as String
            null
        }.`when`(ds).geoRedirectUrlType = Matchers.anyString()
        dsMap[dsId] = ds
        val register = PowerMockito.mock(CacheRegister::class.java)
        Mockito.`when`(
            register.getDeliveryService(
                Matchers.any(
                    HTTPRequest::class.java
                )
            )
        ).thenReturn(null)
        Whitebox.invokeMethod<Any>(handler, "initGeoFailedRedirect", dsMap, register)
        MatcherAssert.assertThat(urlType[0], org.hamcrest.Matchers.equalTo("NOT_DS_URL"))
        MatcherAssert.assertThat(typeUrl[0], org.hamcrest.Matchers.equalTo(path))
    }

    @Test
    @Throws(Exception::class)
    fun itTestNotThisDsUrl() {
        val path = "/ds/url"
        val redirectUrl = "http://test.com$path"
        val dsId = "relative-ds"
        val anotherId = "another-ds"
        val urlType = arrayOf("")
        val typeUrl = arrayOf("")
        val dsMap: MutableMap<String, DeliveryService> = HashMap()
        val ds = Mockito.mock(DeliveryService::class.java)
        Mockito.`when`(ds.id).thenReturn(dsId)
        Mockito.`when`(ds.geoRedirectUrl).thenReturn(redirectUrl)
        Mockito.doAnswer { invocation ->
            val args = invocation.arguments
            typeUrl[0] = args[0] as String
            null
        }.`when`(ds).geoRedirectFile = Matchers.anyString()
        Mockito.doAnswer { invocation ->
            val args = invocation.arguments
            urlType[0] = args[0] as String
            null
        }.`when`(ds).geoRedirectUrlType = Matchers.anyString()
        dsMap[dsId] = ds
        val anotherDs = Mockito.mock(DeliveryService::class.java)
        Mockito.`when`(ds.id).thenReturn(anotherId)
        val register = PowerMockito.mock(CacheRegister::class.java)
        Mockito.`when`(
            register.getDeliveryService(
                Matchers.any(
                    HTTPRequest::class.java
                )
            )
        ).thenReturn(anotherDs)
        Whitebox.invokeMethod<Any>(handler, "initGeoFailedRedirect", dsMap, register)
        MatcherAssert.assertThat(urlType[0], org.hamcrest.Matchers.equalTo("NOT_DS_URL"))
        MatcherAssert.assertThat(typeUrl[0], org.hamcrest.Matchers.equalTo(path))
    }

    @Test
    @Throws(Exception::class)
    fun itTestThisDsUrl() {
        val path = "/ds/url"
        val redirectUrl = "http://test.com$path"
        val dsId = "relative-ds"
        val urlType = arrayOf("")
        val typeUrl = arrayOf("")
        val dsMap: MutableMap<String, DeliveryService> = HashMap()
        val ds = Mockito.mock(DeliveryService::class.java)
        Mockito.`when`(ds.id).thenReturn(dsId)
        Mockito.`when`(ds.geoRedirectUrl).thenReturn(redirectUrl)
        Mockito.doAnswer { invocation ->
            val args = invocation.arguments
            typeUrl[0] = args[0] as String
            null
        }.`when`(ds).geoRedirectFile = Matchers.anyString()
        Mockito.doAnswer { invocation ->
            val args = invocation.arguments
            urlType[0] = args[0] as String
            null
        }.`when`(ds).geoRedirectUrlType = Matchers.anyString()
        dsMap[dsId] = ds
        val register = PowerMockito.mock(CacheRegister::class.java)
        Mockito.`when`(
            register.getDeliveryService(
                Matchers.any(
                    HTTPRequest::class.java
                )
            )
        ).thenReturn(ds)
        Whitebox.invokeMethod<Any>(handler, "initGeoFailedRedirect", dsMap, register)
        MatcherAssert.assertThat(urlType[0], org.hamcrest.Matchers.equalTo("DS_URL"))
        MatcherAssert.assertThat(typeUrl[0], org.hamcrest.Matchers.equalTo(path))
    }

    @Test
    @Throws(Exception::class)
    fun itParsesTheTopologiesConfig() {
        /* Make the CacheLocation, add a Cache, and add the CacheLocation to the CacheRegister */
        val cacheId = "edge"
        val cache = Cache(cacheId, cacheId, 0)
        val location = "CDN_in_a_Box_Edge"
        val cacheLocation = CacheLocation(location, Geolocation(38.897663, 38.897663))
        cacheLocation.addCache(cache)
        val locations: MutableSet<CacheLocation> = HashSet()
        locations.add(cacheLocation)
        val register = CacheRegister()
        register.setConfiguredLocations(locations)

        /* Add a capability to the Cache */
        val capability = "a-capability"
        val capabilities: MutableSet<String> = HashSet()
        capabilities.add(capability)
        cache.addCapabilities(capabilities)

        /* Mock a DeliveryService and add it to our DeliveryService Map */
        val dsId = "top-ds"
        val routingName = "cdn"
        val domain = "ds.site.com"
        val topology = "foo"
        val superHackedRegexp = "(.*\\.|^)$dsId\\..*"
        val ds = Mockito.mock(DeliveryService::class.java)
        Mockito.`when`(ds.id).thenReturn(dsId)
        Mockito.`when`(ds.domain).thenReturn(domain)
        Mockito.`when`(ds.getRemap(superHackedRegexp)).thenReturn(domain)
        Mockito.`when`(ds.routingName).thenReturn(routingName)
        Mockito.`when`(ds.topology).thenReturn(topology)
        Mockito.`when`(ds.hasRequiredCapabilities(capabilities)).thenReturn(true)
        Mockito.`when`(ds.isDns).thenReturn(false)
        val dsMap: MutableMap<String, DeliveryService> = HashMap()
        dsMap[dsId] = ds
        val dsMatcher = DeliveryServiceMatcher(ds)
        dsMatcher.addMatch(DeliveryServiceMatcher.Type.HOST, superHackedRegexp, "")
        val dsMatchers = TreeSet<DeliveryServiceMatcher>()
        dsMatchers.add(dsMatcher)
        register.setDeliveryServiceMap(dsMap)
        register.setDeliveryServiceMatchers(dsMatchers)

        /* Parse the Topologies config JSON */
        val mapper = ObjectMapper()
        val allTopologiesJson = mapper.readTree("{\"$topology\":{\"nodes\":[\"$location\"]}}")
        Whitebox.setInternalState(handler, "statTracker", StatTracker())
        Whitebox.invokeMethod<Any>(handler, "parseTopologyConfig", allTopologiesJson, dsMap, register)

        /* Assert that the DeliveryService was assigned to the Cache */
        val dsReferences = cache.deliveryServices
        MatcherAssert.assertThat(dsReferences.size, org.hamcrest.Matchers.equalTo(1))
        MatcherAssert.assertThat(dsReferences.iterator().next().deliveryServiceId, org.hamcrest.Matchers.equalTo(dsId))
    }

    @Test
    @Throws(Exception::class)
    fun testParseLocalizationMethods() {
        val allMethods = arrayOf(
            LocalizationMethod.CZ,
            LocalizationMethod.DEEP_CZ,
            LocalizationMethod.GEO
        )
        val expected: MutableSet<LocalizationMethod> = HashSet()
        expected.addAll(Arrays.asList(*allMethods))
        val mapper = ObjectMapper()
        val allMethodsString = "{\"localizationMethods\": [\"CZ\",\"DEEP_CZ\",\"GEO\"]}"
        val allMethodsJson = mapper.readTree(allMethodsString)
        var actual =
            Whitebox.invokeMethod<Set<LocalizationMethod>>(handler, "parseLocalizationMethods", "foo", allMethodsJson)
        MatcherAssert.assertThat(actual, org.hamcrest.Matchers.equalTo(expected))
        val noMethodsString = "{}"
        val noMethodsJson = mapper.readTree(noMethodsString)
        actual = Whitebox.invokeMethod(handler, "parseLocalizationMethods", "foo", noMethodsJson)
        MatcherAssert.assertThat(actual, org.hamcrest.Matchers.equalTo(expected))
        val nullMethodsString = "{\"localizationMethods\": null}"
        val nullMethodsJson = mapper.readTree(nullMethodsString)
        actual = Whitebox.invokeMethod(handler, "parseLocalizationMethods", "foo", nullMethodsJson)
        MatcherAssert.assertThat(actual, org.hamcrest.Matchers.equalTo(expected))
        val CZMethodsString = "{\"localizationMethods\": [\"CZ\"]}"
        val CZMethodsJson = mapper.readTree(CZMethodsString)
        expected.clear()
        expected.add(LocalizationMethod.CZ)
        actual = Whitebox.invokeMethod(handler, "parseLocalizationMethods", "foo", CZMethodsJson)
        MatcherAssert.assertThat(actual, org.hamcrest.Matchers.equalTo(expected))
    }
}