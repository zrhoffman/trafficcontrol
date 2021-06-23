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
package com.comcast.cdn.traffic_control.traffic_router.core.hashing

import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.beans.factory.annotation.Autowired
import com.comcast.cdn.traffic_control.traffic_router.core.util.DataExporter
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.ResponseBody
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RequestMethod
import com.comcast.cdn.traffic_control.traffic_router.core.status.model.CacheModel
import com.comcast.cdn.traffic_control.traffic_router.core.ds.SteeringRegistry
import org.springframework.http.ResponseEntity
import com.comcast.cdn.traffic_control.traffic_router.core.ds.Steering
import com.comcast.cdn.traffic_control.traffic_router.core.router.TrafficRouterManager
import com.comcast.cdn.traffic_control.traffic_router.core.edge.CacheLocation
import com.comcast.cdn.traffic_control.traffic_router.core.edge.Node.IPVersions
import com.comcast.cdn.traffic_control.traffic_router.api.controllers.ConsistentHashController
import com.comcast.cdn.traffic_control.traffic_router.core.ds.DeliveryService
import java.net.URLDecoder
import com.comcast.cdn.traffic_control.traffic_router.core.router.TrafficRouter
import com.comcast.cdn.traffic_control.traffic_router.core.request.HTTPRequest
import com.comcast.cdn.traffic_control.traffic_router.core.ds.SteeringTarget
import com.comcast.cdn.traffic_control.traffic_router.core.ds.SteeringFilter
import com.comcast.cdn.traffic_control.traffic_router.core.ds.Dispersion
import com.comcast.cdn.traffic_control.traffic_router.core.util.JsonUtils
import com.comcast.cdn.traffic_control.traffic_router.core.hash.DefaultHashable
import com.comcast.cdn.traffic_control.traffic_router.geolocation.Geolocation
import com.comcast.cdn.traffic_control.traffic_router.core.ds.DeliveryService.DeepCachingType
import com.comcast.cdn.traffic_control.traffic_router.core.util.JsonUtilsException
import kotlin.Throws
import java.net.MalformedURLException
import com.comcast.cdn.traffic_control.traffic_router.core.router.StatTracker.Track.ResultType
import com.comcast.cdn.traffic_control.traffic_router.core.router.StatTracker.Track.ResultDetails
import java.lang.StringBuilder
import com.comcast.cdn.traffic_control.traffic_router.core.edge.Cache.DeliveryServiceReference
import com.comcast.cdn.traffic_control.traffic_router.core.request.DNSRequest
import com.comcast.cdn.traffic_control.traffic_router.core.edge.InetRecord
import java.net.InetAddress
import com.comcast.cdn.traffic_control.traffic_router.core.ds.DeliveryService.TransInfoType
import java.io.IOException
import java.security.GeneralSecurityException
import java.io.DataOutputStream
import java.io.UnsupportedEncodingException
import java.lang.StringBuffer
import com.comcast.cdn.traffic_control.traffic_router.core.util.StringProtector
import java.util.concurrent.atomic.AtomicInteger
import java.lang.IllegalArgumentException
import com.comcast.cdn.traffic_control.traffic_router.core.util.AbstractResourceWatcher
import com.comcast.cdn.traffic_control.traffic_router.core.ds.SteeringWatcher
import java.util.function.BiConsumer
import com.comcast.cdn.traffic_control.traffic_router.core.ds.DeliveryServiceMatcher
import com.comcast.cdn.traffic_control.traffic_router.core.ds.LetsEncryptDnsChallenge
import com.comcast.cdn.traffic_control.traffic_router.core.ds.SteeringResult
import com.comcast.cdn.traffic_control.traffic_router.core.config.ConfigHandler
import java.time.Instant
import com.comcast.cdn.traffic_control.traffic_router.core.ds.LetsEncryptDnsChallengeWatcher
import java.io.FileInputStream
import java.io.BufferedReader
import java.net.ServerSocket
import com.comcast.cdn.traffic_control.traffic_router.core.dns.protocol.TCP.TCPSocketHandler
import com.comcast.cdn.traffic_control.traffic_router.core.dns.protocol.TCP
import java.io.DataInputStream
import org.xbill.DNS.WireParseException
import java.net.DatagramSocket
import com.comcast.cdn.traffic_control.traffic_router.core.dns.protocol.UDP
import com.comcast.cdn.traffic_control.traffic_router.core.dns.protocol.UDP.UDPPacketHandler
import org.xbill.DNS.OPTRecord
import java.lang.Runnable
import java.util.concurrent.ExecutorService
import com.comcast.cdn.traffic_control.traffic_router.core.dns.NameServer
import com.comcast.cdn.traffic_control.traffic_router.core.dns.DNSAccessRecord
import com.comcast.cdn.traffic_control.traffic_router.core.dns.DNSAccessEventBuilder
import java.util.concurrent.TimeUnit
import java.lang.InterruptedException
import java.util.concurrent.ExecutionException
import org.xbill.DNS.Rcode
import com.comcast.cdn.traffic_control.traffic_router.core.dns.ZoneKey
import org.xbill.DNS.RRset
import com.comcast.cdn.traffic_control.traffic_router.core.dns.RRsetKey
import java.text.SimpleDateFormat
import com.comcast.cdn.traffic_control.traffic_router.core.dns.ZoneUtils
import java.lang.RuntimeException
import org.xbill.DNS.EDNSOption
import org.xbill.DNS.DClass
import org.xbill.DNS.ExtendedFlags
import org.xbill.DNS.ClientSubnetOption
import com.comcast.cdn.traffic_control.traffic_router.core.dns.ZoneManager
import org.xbill.DNS.SetResponse
import org.xbill.DNS.SOARecord
import com.comcast.cdn.traffic_control.traffic_router.core.dns.DnsSecKeyPair
import java.util.concurrent.ConcurrentMap
import com.comcast.cdn.traffic_control.traffic_router.core.dns.RRSIGCacheKey
import org.xbill.DNS.RRSIGRecord
import org.xbill.DNS.DNSKEYRecord
import org.xbill.DNS.DSRecord
import com.comcast.cdn.traffic_control.traffic_router.core.router.StatTracker
import com.comcast.cdn.traffic_control.traffic_router.core.util.TrafficOpsUtils
import com.comcast.cdn.traffic_control.traffic_router.core.edge.CacheRegister
import com.comcast.cdn.traffic_control.traffic_router.core.dns.SignatureManager
import com.comcast.cdn.traffic_control.traffic_router.core.router.DNSRouteResult
import org.xbill.DNS.ARecord
import org.xbill.DNS.AAAARecord
import org.xbill.DNS.TextParseException
import java.net.Inet6Address
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.BlockingQueue
import java.util.concurrent.LinkedBlockingQueue
import com.comcast.cdn.traffic_control.traffic_router.core.dns.ZoneManager.ZoneCacheType
import java.util.concurrent.Callable
import java.util.stream.Collectors
import com.google.common.cache.CacheBuilderSpec
import java.io.FileWriter
import com.google.common.cache.CacheBuilder
import com.google.common.cache.CacheLoader
import com.comcast.cdn.traffic_control.traffic_router.core.dns.SignedZoneKey
import java.security.NoSuchAlgorithmException
import com.comcast.cdn.traffic_control.traffic_router.geolocation.GeolocationException
import com.comcast.cdn.traffic_control.traffic_router.core.edge.TrafficRouterLocation
import org.xbill.DNS.NSECRecord
import org.xbill.DNS.CNAMERecord
import org.xbill.DNS.TXTRecord
import org.xbill.DNS.NSRecord
import java.security.PrivateKey
import java.security.PublicKey
import com.comcast.cdn.traffic_control.traffic_router.core.dns.RRSetsBuilder
import java.util.function.ToLongFunction
import com.comcast.cdn.traffic_control.traffic_router.core.dns.NameServerMain
import kotlin.jvm.JvmStatic
import org.springframework.context.support.ClassPathXmlApplicationContext
import com.comcast.cdn.traffic_control.traffic_router.core.dns.ZoneSigner
import java.util.stream.StreamSupport
import org.xbill.DNS.DNSSEC
import org.xbill.DNS.DNSSEC.DNSSECException
import com.comcast.cdn.traffic_control.traffic_router.core.dns.ZoneSignerImpl
import java.util.function.BiFunction
import java.util.function.ToIntFunction
import com.comcast.cdn.traffic_control.traffic_router.core.util.ProtectedFetcher
import com.comcast.cdn.traffic_control.traffic_router.core.dns.DnsSecKeyPairImpl
import java.util.function.BinaryOperator
import com.comcast.cdn.traffic_control.traffic_router.secure.BindPrivateKey
import java.io.ByteArrayInputStream
import org.xbill.DNS.Master
import java.text.DecimalFormat
import java.math.RoundingMode
import com.comcast.cdn.traffic_control.traffic_router.core.loc.FederationMapping
import com.comcast.cdn.traffic_control.traffic_router.core.loc.Federation
import com.comcast.cdn.traffic_control.traffic_router.core.util.CidrAddress
import com.comcast.cdn.traffic_control.traffic_router.core.loc.AnonymousIpWhitelist
import com.comcast.cdn.traffic_control.traffic_router.core.loc.AnonymousIp
import com.comcast.cdn.traffic_control.traffic_router.core.loc.NetworkNodeException
import com.google.common.net.InetAddresses
import com.maxmind.geoip2.model.AnonymousIpResponse
import com.comcast.cdn.traffic_control.traffic_router.core.router.HTTPRouteResult
import kotlin.jvm.JvmOverloads
import com.comcast.cdn.traffic_control.traffic_router.core.loc.NetworkNode
import com.comcast.cdn.traffic_control.traffic_router.core.loc.NetworkNode.SuperNode
import com.comcast.cdn.traffic_control.traffic_router.core.loc.RegionalGeoDsvc
import com.comcast.cdn.traffic_control.traffic_router.core.loc.RegionalGeoRule
import com.comcast.cdn.traffic_control.traffic_router.core.loc.RegionalGeo
import com.comcast.cdn.traffic_control.traffic_router.core.loc.RegionalGeoRule.PostalsType
import com.comcast.cdn.traffic_control.traffic_router.core.loc.RegionalGeoCoordinateRange
import com.comcast.cdn.traffic_control.traffic_router.core.loc.RegionalGeoResult
import com.comcast.cdn.traffic_control.traffic_router.core.loc.RegionalGeoResult.RegionalGeoResultType
import com.comcast.cdn.traffic_control.traffic_router.core.loc.AbstractServiceUpdater
import com.comcast.cdn.traffic_control.traffic_router.core.util.ComparableTreeSet
import com.comcast.cdn.traffic_control.traffic_router.core.loc.NetworkUpdater
import com.comcast.cdn.traffic_control.traffic_router.core.loc.FederationMappingBuilder
import com.comcast.cdn.traffic_control.traffic_router.core.loc.FederationRegistry
import com.comcast.cdn.traffic_control.traffic_router.core.loc.FederationsBuilder
import com.comcast.cdn.traffic_control.traffic_router.core.loc.FederationsWatcher
import com.comcast.cdn.traffic_control.traffic_router.core.loc.RegionalGeoUpdater
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.zip.GZIPInputStream
import java.io.FileOutputStream
import com.comcast.cdn.traffic_control.traffic_router.core.loc.AnonymousIpConfigUpdater
import com.comcast.cdn.traffic_control.traffic_router.geolocation.GeolocationService
import com.maxmind.geoip2.DatabaseReader
import com.maxmind.geoip2.model.CityResponse
import com.maxmind.geoip2.exception.AddressNotFoundException
import com.comcast.cdn.traffic_control.traffic_router.core.loc.MaxmindGeolocationService
import com.comcast.cdn.traffic_control.traffic_router.core.loc.AnonymousIpDatabaseService
import com.maxmind.geoip2.exception.GeoIp2Exception
import com.comcast.cdn.traffic_control.traffic_router.core.loc.AnonymousIpDatabaseUpdater
import org.apache.commons.lang3.builder.HashCodeBuilder
import java.net.Inet4Address
import com.comcast.cdn.traffic_control.traffic_router.core.edge.CacheLocation.LocalizationMethod
import com.comcast.cdn.traffic_control.traffic_router.core.hash.Hashable
import com.comcast.cdn.traffic_control.traffic_router.core.hash.NumberSearcher
import com.comcast.cdn.traffic_control.traffic_router.core.hash.MD5HashFunction
import org.springframework.web.filter.OncePerRequestFilter
import com.comcast.cdn.traffic_control.traffic_router.core.http.HTTPAccessRecord
import com.comcast.cdn.traffic_control.traffic_router.core.http.RouterFilter
import com.comcast.cdn.traffic_control.traffic_router.core.http.HTTPAccessEventBuilder
import com.comcast.cdn.traffic_control.traffic_router.core.http.HttpAccessRequestHeaders
import javax.net.ssl.TrustManager
import com.comcast.cdn.traffic_control.traffic_router.core.util.Fetcher.DefaultTrustManager
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.X509TrustManager
import javax.net.ssl.HostnameVerifier
import javax.net.ssl.SSLSession
import java.lang.NumberFormatException
import com.comcast.cdn.traffic_control.traffic_router.core.util.FederationExporter
import com.comcast.cdn.traffic_control.traffic_router.core.edge.PropertiesAndCaches
import com.comcast.cdn.traffic_control.traffic_router.core.util.LanguidState
import javax.crypto.SecretKeyFactory
import javax.crypto.SecretKey
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.PBEParameterSpec
import com.comcast.cdn.traffic_control.traffic_router.core.util.ResourceUrl
import com.comcast.cdn.traffic_control.traffic_router.core.config.WatcherConfig
import java.io.FileReader
import com.comcast.cdn.traffic_control.traffic_router.core.util.AbstractUpdatable
import org.asynchttpclient.AsyncHttpClient
import com.comcast.cdn.traffic_control.traffic_router.core.util.PeriodicResourceUpdater
import org.asynchttpclient.DefaultAsyncHttpClient
import org.asynchttpclient.DefaultAsyncHttpClientConfig
import org.asynchttpclient.AsyncCompletionHandler
import java.net.URISyntaxException
import com.comcast.cdn.traffic_control.traffic_router.core.util.ComparableStringByLength
import com.comcast.cdn.traffic_control.traffic_router.core.loc.GeolocationDatabaseUpdater
import com.comcast.cdn.traffic_control.traffic_router.core.loc.DeepNetworkUpdater
import com.comcast.cdn.traffic_control.traffic_router.core.secure.CertificatesPoller
import com.comcast.cdn.traffic_control.traffic_router.core.secure.CertificatesPublisher
import java.util.concurrent.atomic.AtomicBoolean
import com.comcast.cdn.traffic_control.traffic_router.core.monitor.TrafficMonitorWatcher
import com.comcast.cdn.traffic_control.traffic_router.shared.CertificateData
import com.comcast.cdn.traffic_control.traffic_router.core.config.CertificateChecker
import com.comcast.cdn.traffic_control.traffic_router.core.router.StatTracker.Track.RouteType
import com.comcast.cdn.traffic_control.traffic_router.core.router.StatTracker.Track.ResultCode
import com.comcast.cdn.traffic_control.traffic_router.core.router.StatTracker.Tallies
import com.comcast.cdn.traffic_control.traffic_router.core.hash.ConsistentHasher
import com.comcast.cdn.traffic_control.traffic_router.core.ds.SteeringGeolocationComparator
import com.comcast.cdn.traffic_control.traffic_router.core.router.LocationComparator
import org.springframework.beans.BeansException
import com.comcast.cdn.traffic_control.traffic_router.configuration.ConfigurationListener
import com.comcast.cdn.traffic_control.traffic_router.core.router.RouteResult
import org.springframework.context.ApplicationListener
import org.springframework.context.event.ContextRefreshedEvent
import com.comcast.cdn.traffic_control.traffic_router.core.secure.CertificatesClient
import com.comcast.cdn.traffic_control.traffic_router.core.secure.CertificatesResponse
import org.springframework.core.env.Environment
import javax.management.ObjectName
import com.comcast.cdn.traffic_control.traffic_router.shared.DeliveryServiceCertificatesMBean
import org.springframework.context.event.ApplicationContextEvent
import com.comcast.cdn.traffic_control.traffic_router.core.monitor.TrafficMonitorResourceUrl
import org.springframework.context.event.ContextClosedEvent
import org.powermock.core.classloader.annotations.PrepareForTest
import org.junit.runner.RunWith
import org.powermock.modules.junit4.PowerMockRunner
import org.junit.Before
import com.comcast.cdn.traffic_control.traffic_router.shared.ZoneTestRecords
import org.powermock.api.mockito.PowerMockito
import com.comcast.cdn.traffic_control.traffic_router.core.dns.protocol.AbstractProtocolTest.FakeAbstractProtocol
import java.lang.System
import com.comcast.cdn.traffic_control.traffic_router.core.dns.protocol.AbstractProtocolTest
import com.comcast.cdn.traffic_control.traffic_router.core.util.IntegrationTest
import com.comcast.cdn.traffic_control.traffic_router.core.dns.ZoneManagerTest
import org.junit.BeforeClass
import java.nio.file.Paths
import com.comcast.cdn.traffic_control.traffic_router.core.TestBase
import com.comcast.cdn.traffic_control.traffic_router.core.dns.DNSException
import com.comcast.cdn.traffic_control.traffic_router.core.dns.ZoneSignerImplTest.IsRRsetTypeA
import com.comcast.cdn.traffic_control.traffic_router.core.dns.ZoneSignerImplTest.IsRRsetTypeNSEC
import com.comcast.cdn.traffic_control.traffic_router.core.loc.GeoTest
import com.comcast.cdn.traffic_control.traffic_router.core.loc.NetworkNodeTest
import com.comcast.cdn.traffic_control.traffic_router.core.loc.MaxmindGeoIP2Test
import com.comcast.cdn.traffic_control.traffic_router.core.loc.AbstractServiceUpdaterTest.Updater
import com.comcast.cdn.traffic_control.traffic_router.core.loc.AnonymousIpDatabaseServiceTest
import org.powermock.core.classloader.annotations.PowerMockIgnore
import com.comcast.cdn.traffic_control.traffic_router.core.util.AbstractResourceWatcherTest
import java.lang.Void
import com.comcast.cdn.traffic_control.traffic_router.core.router.StatelessTrafficRouterTest
import org.bouncycastle.jce.provider.BouncyCastleProvider
import com.comcast.cdn.traffic_control.traffic_router.secure.Pkcs1
import java.security.spec.KeySpec
import java.security.spec.PKCS8EncodedKeySpec
import com.comcast.cdn.traffic_control.traffic_router.core.util.ExternalTest
import org.apache.http.impl.client.CloseableHttpClient
import org.apache.catalina.LifecycleException
import org.apache.http.impl.client.HttpClientBuilder
import org.apache.http.client.methods.HttpGet
import org.apache.http.client.methods.CloseableHttpResponse
import org.apache.http.util.EntityUtils
import org.junit.FixMethodOrder
import org.junit.runners.MethodSorters
import java.security.KeyStore
import javax.net.ssl.TrustManagerFactory
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
import org.hamcrest.number.IsCloseTo
import java.net.InetSocketAddress
import com.sun.net.httpserver.HttpExchange
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
import org.junit.AfterClass
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes
import java.nio.file.FileVisitResult
import org.hamcrest.number.OrderingComparison
import javax.management.MBeanServer
import com.comcast.cdn.traffic_control.traffic_router.shared.DeliveryServiceCertificates
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.context.support.FileSystemXmlApplicationContext
import org.apache.catalina.startup.Catalina
import org.apache.catalina.core.StandardService
import org.apache.catalina.core.StandardHost
import org.apache.catalina.core.StandardContext
import org.hamcrest.core.IsEqual
import org.junit.Assert
import org.junit.Test
import org.mockito.InjectMocks
import org.mockito.Matchers
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.MockitoAnnotations
import java.lang.Exception
import java.util.*

class ConsistentHasherTest constructor() {
    @Mock
    var md5HashFunction: MD5HashFunction? = MD5HashFunction()

    @Mock
    var numberSearcher: NumberSearcher? = NumberSearcher()

    @InjectMocks
    var hashable1: DefaultHashable? = DefaultHashable()

    @InjectMocks
    var hashable2: DefaultHashable? = DefaultHashable()

    @InjectMocks
    var hashable3: DefaultHashable? = DefaultHashable()
    var hashables: MutableList<DefaultHashable?>? = ArrayList()

    @InjectMocks
    var consistentHasher: ConsistentHasher? = null
    var trafficRouter: TrafficRouter? = null
    @Before
    fun before() {
        hashable1.generateHashes("hashId1", 100)
        hashable2.generateHashes("hashId2", 100)
        hashable3.generateHashes("hashId3", 100)
        hashables.add(hashable1)
        hashables.add(hashable2)
        hashables.add(hashable3)
        trafficRouter = Mockito.mock(TrafficRouter::class.java)
        Mockito.`when`(trafficRouter.buildPatternBasedHashString(Matchers.anyString(), Matchers.anyString()))
            .thenCallRealMethod()
        Mockito.`when`(
            trafficRouter.buildPatternBasedHashString(
                Matchers.any(
                    DeliveryService::class.java
                ), Matchers.any(HTTPRequest::class.java)
            )
        ).thenCallRealMethod()
        MockitoAnnotations.initMocks(this)
    }

    @Test
    @Throws(Exception::class)
    fun itHashes() {
        val mapper: ObjectMapper? = ObjectMapper()
        val hashable: DefaultHashable? =
            consistentHasher.selectHashable(hashables, Dispersion(mapper.createObjectNode()), "some-string")
        Assert.assertThat(
            hashable,
            org.hamcrest.Matchers.anyOf(
                IsEqual.equalTo(hashable1),
                IsEqual.equalTo(hashable2),
                IsEqual.equalTo(hashable3)
            )
        )
        val nextHashable: DefaultHashable? =
            consistentHasher.selectHashable(hashables, Dispersion(mapper.createObjectNode()), "some-string")
        Assert.assertThat(nextHashable, IsEqual.equalTo(hashable))
    }

    @Test
    @Throws(Exception::class)
    fun itHashesMoreThanOne() {
        val jsonStr: String? = ("{\"dispersion\": {\n" +
                "\"limit\": 2,\n" +
                "\"shuffled\": \"true\"\n" +
                "}}")
        val mapper: ObjectMapper? = ObjectMapper()
        val jo: JsonNode? = mapper.readTree(jsonStr)
        val dispersion: Dispersion? = Dispersion(jo)
        val results: MutableList<DefaultHashable?>? =
            consistentHasher.selectHashables(hashables, dispersion, "some-string")
        Assert.assertThat(results.size, IsEqual.equalTo(2))
        Assert.assertThat(
            results.get(0),
            org.hamcrest.Matchers.anyOf(
                IsEqual.equalTo(hashable1),
                IsEqual.equalTo(hashable2),
                IsEqual.equalTo(hashable3)
            )
        )
        Assert.assertThat(
            results.get(1),
            org.hamcrest.Matchers.anyOf(
                IsEqual.equalTo(hashable1),
                IsEqual.equalTo(hashable2),
                IsEqual.equalTo(hashable3)
            )
        )
        val results2: MutableList<DefaultHashable?>? =
            consistentHasher.selectHashables(hashables, dispersion, "some-string")
        assert((results.containsAll(results2)))
        val jsonStr2: String? = ("{\"dispersion\": {\n" +
                "\"limit\": 2000000000,\n" +
                "\"shuffled\": \"true\"\n" +
                "}}")
        val jo2: JsonNode? = mapper.readTree(jsonStr2)
        val disp2: Dispersion? = Dispersion(jo2)
        val res3: MutableList<DefaultHashable?>? = consistentHasher.selectHashables(hashables, disp2, "some-string")
        assert((res3.containsAll(hashables)))
    }

    @Test
    fun itemsMigrateFromSmallerToLargerBucket() {
        val randomPaths: MutableList<String?>? = ArrayList()
        for (i in 0..9999) {
            randomPaths.add(generateRandomPath())
        }
        val smallerBucket: Hashable<*>? = DefaultHashable().generateHashes("Small One", 10000)
        val largerBucket: Hashable<*>? = DefaultHashable().generateHashes("Larger bucket", 90000)
        val buckets: MutableList<Hashable<*>?>? = ArrayList()
        buckets.add(smallerBucket)
        buckets.add(largerBucket)
        val hashedPaths: MutableMap<Hashable<*>?, MutableList<String?>?>? = HashMap()
        hashedPaths.put(smallerBucket, ArrayList())
        hashedPaths.put(largerBucket, ArrayList())
        val mapper: ObjectMapper? = ObjectMapper()
        for (randomPath: String? in randomPaths) {
            val hashable: Hashable<*>? =
                consistentHasher.selectHashable(buckets, Dispersion(mapper.createObjectNode()), randomPath)
            hashedPaths.get(hashable).add(randomPath)
        }
        val grownBucket: Hashable<*>? = DefaultHashable().generateHashes("Small One", 20000)
        val shrunkBucket: Hashable<*>? = DefaultHashable().generateHashes("Larger bucket", 80000)
        val changedBuckets: MutableList<Hashable<*>?>? = ArrayList()
        changedBuckets.add(grownBucket)
        changedBuckets.add(shrunkBucket)
        val rehashedPaths: MutableMap<Hashable<*>?, MutableList<String?>?>? = HashMap()
        rehashedPaths.put(grownBucket, ArrayList())
        rehashedPaths.put(shrunkBucket, ArrayList())
        for (randomPath: String? in randomPaths) {
            val hashable: Hashable<*>? =
                consistentHasher.selectHashable(changedBuckets, Dispersion(mapper.createObjectNode()), randomPath)
            rehashedPaths.get(hashable).add(randomPath)
        }
        Assert.assertThat(
            rehashedPaths.get(grownBucket).size,
            org.hamcrest.Matchers.greaterThan(hashedPaths.get(smallerBucket).size)
        )
        Assert.assertThat(
            rehashedPaths.get(shrunkBucket).size,
            org.hamcrest.Matchers.lessThan(hashedPaths.get(largerBucket).size)
        )
        for (path: String? in hashedPaths.get(smallerBucket)) {
            Assert.assertThat(rehashedPaths.get(grownBucket).contains(path), IsEqual.equalTo(true))
        }
        for (path: String? in rehashedPaths.get(shrunkBucket)) {
            Assert.assertThat(hashedPaths.get(largerBucket).contains(path), IsEqual.equalTo(true))
        }
    }

    @Test
    @Throws(Exception::class)
    fun testPatternBasedHashing() {
        // use regex to standardize path
        val regex: String? = "/.*?(/.*?/).*?(.m3u8)"
        val expectedResult: String? = "/some_stream_name1234/.m3u8"
        var requestPath: String? = "/path12341234/some_stream_name1234/some_info4321.m3u8"
        var pathToHash: String? = trafficRouter.buildPatternBasedHashString(regex, requestPath)
        Assert.assertThat(pathToHash, IsEqual.equalTo(expectedResult))
        val hashableResult1: DefaultHashable? = consistentHasher.selectHashable(hashables, null, pathToHash)
        requestPath = "/pathasdf1234/some_stream_name1234/some_other_info.m3u8"
        pathToHash = trafficRouter.buildPatternBasedHashString(regex, requestPath)
        Assert.assertThat(pathToHash, IsEqual.equalTo(expectedResult))
        val hashableResult2: DefaultHashable? = consistentHasher.selectHashable(hashables, null, pathToHash)
        requestPath = "/path4321fdsa/some_stream_name1234/4321some_info.m3u8"
        pathToHash = trafficRouter.buildPatternBasedHashString(regex, requestPath)
        Assert.assertThat(pathToHash, IsEqual.equalTo(expectedResult))
        val hashableResult3: DefaultHashable? = consistentHasher.selectHashable(hashables, null, pathToHash)
        requestPath = "/1234pathfdas/some_stream_name1234/some_info.m3u8"
        pathToHash = trafficRouter.buildPatternBasedHashString(regex, requestPath)
        Assert.assertThat(pathToHash, IsEqual.equalTo(expectedResult))
        val hashableResult4: DefaultHashable? = consistentHasher.selectHashable(hashables, null, pathToHash)
        Assert.assertThat(
            hashableResult1,
            org.hamcrest.Matchers.allOf(
                IsEqual.equalTo(hashableResult2),
                IsEqual.equalTo(hashableResult3),
                IsEqual.equalTo(hashableResult4)
            )
        )
    }

    @Test
    @Throws(Exception::class)
    fun itHashesQueryParams() {
        val j: JsonNode? =
            (ObjectMapper()).readTree("{\"routingName\":\"edge\",\"coverageZoneOnly\":false,\"consistentHashQueryParams\":[\"test\", \"quest\"]}")
        val d: DeliveryService? = DeliveryService("test", j)
        val r1: HTTPRequest? = HTTPRequest()
        r1.setPath("/path1234/some_stream_name1234/some_other_info.m3u8")
        r1.setQueryString("test=value")
        val r2: HTTPRequest? = HTTPRequest()
        r2.setPath(r1.getPath())
        r2.setQueryString("quest=other_value")
        val p1: String? = trafficRouter.buildPatternBasedHashString(d, r1)
        val p2: String? = trafficRouter.buildPatternBasedHashString(d, r2)
        assert(!(p1 == p2))
    }

    var alphanumericCharacters: String? = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWZYZ"
    var exampleValidPathCharacters: String? = alphanumericCharacters + "/=;()-."
    var random: Random? = Random(1462307930227L)
    fun generateRandomPath(): String? {
        val pathLength: Int = 60 + random.nextInt(61)
        val stringBuilder: StringBuilder? = StringBuilder("/")
        for (i in 0..3) {
            val index: Int = random.nextInt(alphanumericCharacters.length)
            stringBuilder.append(alphanumericCharacters.get(index))
        }
        stringBuilder.append("/")
        for (i in 0 until pathLength) {
            val index: Int = random.nextInt(exampleValidPathCharacters.length)
            stringBuilder.append(exampleValidPathCharacters.get(index))
        }
        return stringBuilder.toString()
    }
}