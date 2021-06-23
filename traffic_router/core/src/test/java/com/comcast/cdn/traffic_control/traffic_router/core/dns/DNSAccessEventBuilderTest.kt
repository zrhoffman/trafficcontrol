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
package com.comcast.cdn.traffic_control.traffic_router.core.dns

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
import org.springframework.context.support.FileSystemXmlApplicationContext
import org.apache.catalina.startup.Catalina
import org.apache.catalina.core.StandardService
import org.apache.catalina.core.StandardHost
import org.apache.catalina.core.StandardContext
import org.hamcrest.MatcherAssert
import org.hamcrest.Matchers
import org.junit.Test
import org.mockito.Mockito
import org.xbill.DNS.Header
import org.xbill.DNS.Message
import org.xbill.DNS.Name
import org.xbill.DNS.Section
import org.xbill.DNS.Type
import java.lang.Exception
import java.util.*

@RunWith(PowerMockRunner::class)
@PrepareForTest(Random::class, Header::class, DNSAccessEventBuilder::class, System::class, DNSAccessRecord::class)
class DNSAccessEventBuilderTest constructor() {
    private var client: InetAddress? = null
    private var resolver: InetAddress? = null
    @Before
    @Throws(Exception::class)
    fun before() {
        PowerMockito.mockStatic(System::class.java)
        val random: Random? = Mockito.mock(Random::class.java)
        Mockito.`when`(random.nextInt(0xffff)).thenReturn(65535)
        PowerMockito.whenNew(Random::class.java).withNoArguments().thenReturn(random)
        client = Mockito.mock(InetAddress::class.java)
        Mockito.`when`(client.getHostAddress()).thenReturn("192.168.10.11")
        resolver = Mockito.mock(InetAddress::class.java)
        Mockito.`when`(resolver.getHostAddress()).thenReturn("10.0.0.211")
    }

    @Test
    @Throws(Exception::class)
    fun itCreatesRequestErrorData() {
        Mockito.`when`(System.currentTimeMillis()).thenReturn(144140678789L)
        Mockito.`when`(System.nanoTime()).thenReturn(100000000L, 889000000L)
        val dnsAccessRecord: DNSAccessRecord? = DNSAccessRecord.Builder(144140678000L, client).build()
        val dnsAccessEvent: String? =
            DNSAccessEventBuilder.create(dnsAccessRecord, WireParseException("invalid record length"))
        MatcherAssert.assertThat(
            dnsAccessEvent, Matchers.equalTo(
                "144140678.000 qtype=DNS chi=192.168.10.11 rhi=- ttms=789.000 xn=- fqdn=- type=- class=- rcode=-" +
                        " rtype=- rloc=\"-\" rdtl=- rerr=\"Bad Request:WireParseException:invalid record length\" ttl=\"-\" ans=\"-\""
            )
        )
    }

    @Test
    @Throws(Exception::class)
    fun itAddsResponseData() {
        val name: Name? = Name.fromString("www.example.com.")
        Mockito.`when`(System.nanoTime()).thenReturn(100000000L, 100000000L + 789123000L)
        Mockito.`when`(System.currentTimeMillis()).thenReturn(144140678789L).thenReturn(144140678000L)
        val question: Record? = Record.newRecord(name, Type.A, DClass.IN, 12345L)
        val response: Message? = PowerMockito.spy(Message.newQuery(question))
        response.getHeader().setRcode(Rcode.NOERROR)
        val record1: Record? = Mockito.mock(Record::class.java)
        Mockito.`when`(record1.rdataToString()).thenReturn("foo")
        Mockito.`when`(record1.getTTL()).thenReturn(1L)
        val record2: Record? = Mockito.mock(Record::class.java)
        Mockito.`when`(record2.rdataToString()).thenReturn("bar")
        Mockito.`when`(record2.getTTL()).thenReturn(2L)
        val record3: Record? = Mockito.mock(Record::class.java)
        Mockito.`when`(record3.rdataToString()).thenReturn("baz")
        Mockito.`when`(record3.getTTL()).thenReturn(3L)
        val records: Array<Record?>? = arrayOf(record1, record2, record3)
        Mockito.`when`(response.getSectionArray(Section.ANSWER)).thenReturn(records)
        val answerAddress: InetAddress? = Inet4Address.getByName("192.168.1.23")
        val addressRecord: ARecord? = ARecord(name, DClass.IN, 54321L, answerAddress)
        response.addRecord(addressRecord, Section.ANSWER)
        val dnsAccessRecord: DNSAccessRecord? =
            DNSAccessRecord.Builder(144140678000L, client).dnsMessage(response).build()
        var dnsAccessEvent: String? = DNSAccessEventBuilder.create(dnsAccessRecord)
        MatcherAssert.assertThat(
            dnsAccessEvent, Matchers.equalTo(
                ("144140678.000 qtype=DNS chi=192.168.10.11 rhi=- ttms=789.123" +
                        " xn=65535 fqdn=www.example.com. type=A class=IN" +
                        " rcode=NOERROR rtype=- rloc=\"-\" rdtl=- rerr=\"-\" ttl=\"1 2 3\" ans=\"foo bar baz\"")
            )
        )
        Mockito.`when`(System.nanoTime()).thenReturn(100000000L + 456000L)
        dnsAccessEvent = DNSAccessEventBuilder.create(dnsAccessRecord)
        MatcherAssert.assertThat(
            dnsAccessEvent, Matchers.equalTo(
                ("144140678.000 qtype=DNS chi=192.168.10.11 rhi=- ttms=0.456" +
                        " xn=65535 fqdn=www.example.com. type=A class=IN" +
                        " rcode=NOERROR rtype=- rloc=\"-\" rdtl=- rerr=\"-\" ttl=\"1 2 3\" ans=\"foo bar baz\"")
            )
        )
    }

    @Test
    @Throws(Exception::class)
    fun itCreatesServerErrorData() {
        val query: Message? =
            Message.newQuery(Record.newRecord(Name.fromString("www.example.com."), Type.A, DClass.IN, 12345L))
        Mockito.`when`(System.currentTimeMillis()).thenReturn(144140678789L)
        Mockito.`when`(System.nanoTime()).thenReturn(100000000L, 100000000L + 789876321L)
        val dnsAccessRecord: DNSAccessRecord? = DNSAccessRecord.Builder(144140678000L, client).dnsMessage(query).build()
        val dnsAccessEvent: String? = DNSAccessEventBuilder.create(dnsAccessRecord, RuntimeException("boom it failed"))
        MatcherAssert.assertThat(
            dnsAccessEvent, Matchers.equalTo(
                ("144140678.000 qtype=DNS chi=192.168.10.11 rhi=- ttms=789.876" +
                        " xn=65535 fqdn=www.example.com. type=A class=IN" +
                        " rcode=SERVFAIL rtype=- rloc=\"-\" rdtl=- rerr=\"Server Error:RuntimeException:boom it failed\" ttl=\"-\" ans=\"-\"")
            )
        )
    }

    @Test
    @Throws(Exception::class)
    fun itAddsResultTypeData() {
        val name: Name? = Name.fromString("www.example.com.")
        Mockito.`when`(System.currentTimeMillis()).thenReturn(144140678789L).thenReturn(144140678000L)
        Mockito.`when`(System.nanoTime())
            .thenReturn(100000000L, 100000000L + 789000321L, 100000000L + 123123L, 100000000L + 246001L)
        val question: Record? = Record.newRecord(name, Type.A, DClass.IN, 12345L)
        val response: Message? = PowerMockito.spy(Message.newQuery(question))
        response.getHeader().setRcode(Rcode.NOERROR)
        val record1: Record? = Mockito.mock(Record::class.java)
        Mockito.`when`(record1.rdataToString()).thenReturn("foo")
        Mockito.`when`(record1.getTTL()).thenReturn(1L)
        val record2: Record? = Mockito.mock(Record::class.java)
        Mockito.`when`(record2.rdataToString()).thenReturn("bar")
        Mockito.`when`(record2.getTTL()).thenReturn(2L)
        val record3: Record? = Mockito.mock(Record::class.java)
        Mockito.`when`(record3.rdataToString()).thenReturn("baz")
        Mockito.`when`(record3.getTTL()).thenReturn(3L)
        val records: Array<Record?>? = arrayOf(record1, record2, record3)
        Mockito.`when`(response.getSectionArray(Section.ANSWER)).thenReturn(records)
        val answerAddress: InetAddress? = Inet4Address.getByName("192.168.1.23")
        val addressRecord: ARecord? = ARecord(name, DClass.IN, 54321L, answerAddress)
        response.addRecord(addressRecord, Section.ANSWER)
        val resultLocation: Geolocation? = Geolocation(39.7528, -104.9997)
        val resultType: ResultType? = ResultType.CZ
        val builder: DNSAccessRecord.Builder? = DNSAccessRecord.Builder(144140678000L, client)
            .dnsMessage(response).resultType(resultType).resultLocation(resultLocation)
        var dnsAccessRecord: DNSAccessRecord? = builder.build()
        var dnsAccessEvent: String? = DNSAccessEventBuilder.create(dnsAccessRecord)
        MatcherAssert.assertThat(
            dnsAccessEvent, Matchers.equalTo(
                ("144140678.000 qtype=DNS chi=192.168.10.11 rhi=- ttms=789.000" +
                        " xn=65535 fqdn=www.example.com. type=A class=IN" +
                        " rcode=NOERROR rtype=CZ rloc=\"39.75,-104.99\" rdtl=- rerr=\"-\" ttl=\"1 2 3\" ans=\"foo bar baz\"")
            )
        )
        dnsAccessRecord = builder.resultType(ResultType.GEO).build()
        dnsAccessEvent = DNSAccessEventBuilder.create(dnsAccessRecord)
        MatcherAssert.assertThat(
            dnsAccessEvent, Matchers.equalTo(
                ("144140678.000 qtype=DNS chi=192.168.10.11 rhi=- ttms=0.123" +
                        " xn=65535 fqdn=www.example.com. type=A class=IN" +
                        " rcode=NOERROR rtype=GEO rloc=\"39.75,-104.99\" rdtl=- rerr=\"-\" ttl=\"1 2 3\" ans=\"foo bar baz\"")
            )
        )
        dnsAccessRecord = builder.resultType(ResultType.MISS).resultDetails(ResultDetails.DS_NOT_FOUND).build()
        dnsAccessEvent = DNSAccessEventBuilder.create(dnsAccessRecord)
        MatcherAssert.assertThat(
            dnsAccessEvent, Matchers.equalTo(
                ("144140678.000 qtype=DNS chi=192.168.10.11 rhi=- ttms=0.246" +
                        " xn=65535 fqdn=www.example.com. type=A class=IN" +
                        " rcode=NOERROR rtype=MISS rloc=\"39.75,-104.99\" rdtl=DS_NOT_FOUND rerr=\"-\" ttl=\"1 2 3\" ans=\"foo bar baz\"")
            )
        )
    }

    @Test
    @Throws(Exception::class)
    fun itLogsResolverAndClient() {
        val name: Name? = Name.fromString("www.example.com.")
        Mockito.`when`(System.currentTimeMillis()).thenReturn(144140678789L).thenReturn(144140678000L)
        Mockito.`when`(System.nanoTime())
            .thenReturn(100000000L, 100000000L + 789000321L, 100000000L + 123123L, 100000000L + 246001L)
        val question: Record? = Record.newRecord(name, Type.A, DClass.IN, 12345L)
        val response: Message? = PowerMockito.spy(Message.newQuery(question))
        response.getHeader().setRcode(Rcode.NOERROR)
        val record1: Record? = Mockito.mock(Record::class.java)
        Mockito.`when`(record1.rdataToString()).thenReturn("foo")
        Mockito.`when`(record1.getTTL()).thenReturn(1L)
        val records: Array<Record?>? = arrayOf(record1)
        Mockito.`when`(response.getSectionArray(Section.ANSWER)).thenReturn(records)
        val answerAddress: InetAddress? = Inet4Address.getByName("192.168.1.23")
        val addressRecord: ARecord? = ARecord(name, DClass.IN, 54321L, answerAddress)
        response.addRecord(addressRecord, Section.ANSWER)
        val resultLocation: Geolocation? = Geolocation(39.7528, -104.9997)
        val resultType: ResultType? = ResultType.CZ
        val builder: DNSAccessRecord.Builder? = DNSAccessRecord.Builder(144140678000L, resolver)
            .dnsMessage(response).resultType(resultType).resultLocation(resultLocation).client(client)
        val dnsAccessRecord: DNSAccessRecord? = builder.build()
        val dnsAccessEvent: String? = DNSAccessEventBuilder.create(dnsAccessRecord)
        MatcherAssert.assertThat(
            dnsAccessEvent, Matchers.equalTo(
                ("144140678.000 qtype=DNS chi=192.168.10.11 rhi=10.0.0.211 ttms=789.000" +
                        " xn=65535 fqdn=www.example.com. type=A class=IN" +
                        " rcode=NOERROR rtype=CZ rloc=\"39.75,-104.99\" rdtl=- rerr=\"-\" ttl=\"1\" ans=\"foo\"")
            )
        )
    }
}