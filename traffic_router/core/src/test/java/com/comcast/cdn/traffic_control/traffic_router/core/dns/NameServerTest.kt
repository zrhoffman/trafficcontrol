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
import java.util.HashMap
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
import java.util.SortedMap
import java.util.Collections
import com.comcast.cdn.traffic_control.traffic_router.core.util.JsonUtils
import com.comcast.cdn.traffic_control.traffic_router.core.hash.DefaultHashable
import com.comcast.cdn.traffic_control.traffic_router.geolocation.Geolocation
import java.util.HashSet
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
import java.util.SortedSet
import java.util.TreeSet
import java.io.UnsupportedEncodingException
import java.lang.StringBuffer
import com.comcast.cdn.traffic_control.traffic_router.core.util.StringProtector
import java.util.concurrent.atomic.AtomicInteger
import java.lang.IllegalArgumentException
import com.comcast.cdn.traffic_control.traffic_router.core.util.AbstractResourceWatcher
import com.comcast.cdn.traffic_control.traffic_router.core.ds.SteeringWatcher
import java.util.function.BiConsumer
import com.comcast.cdn.traffic_control.traffic_router.core.ds.DeliveryServiceMatcher
import java.util.TreeMap
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
import java.util.Calendar
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
import java.util.OptionalLong
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
import java.util.NoSuchElementException
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
import java.util.Enumeration
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
import com.fasterxml.jackson.databind.node.ArrayNode
import com.fasterxml.jackson.databind.node.JsonNodeFactory
import com.fasterxml.jackson.databind.node.ObjectNode
import org.springframework.context.support.FileSystemXmlApplicationContext
import org.apache.catalina.startup.Catalina
import org.apache.catalina.core.StandardService
import org.apache.catalina.core.StandardHost
import org.apache.catalina.core.StandardContext
import org.hamcrest.MatcherAssert
import org.junit.Test
import org.mockito.Matchers
import org.mockito.Mockito
import org.xbill.DNS.Header
import org.xbill.DNS.Message
import org.xbill.DNS.Name
import org.xbill.DNS.Section
import org.xbill.DNS.Type
import org.xbill.DNS.Zone
import java.lang.Exception
import java.util.ArrayList

@RunWith(PowerMockRunner::class)
@PrepareForTest(
    Header::class,
    NameServer::class,
    TrafficRouterManager::class,
    TrafficRouter::class,
    CacheRegister::class
)
class NameServerTest constructor() {
    private var nameServer: NameServer? = null
    private var client: InetAddress? = null
    private var trafficRouterManager: TrafficRouterManager? = null
    private var trafficRouter: TrafficRouter? = null
    private var ar: Record? = null
    private var ns: NSRecord? = null
    @Before
    @Throws(Exception::class)
    fun before() {
        client = Inet4Address.getByAddress(byteArrayOf(192 as Byte, 168 as Byte, 23, 45))
        nameServer = NameServer()
        trafficRouterManager = Mockito.mock(TrafficRouterManager::class.java)
        trafficRouter = Mockito.mock(TrafficRouter::class.java)
        val cacheRegister: CacheRegister? = Mockito.mock(CacheRegister::class.java)
        Mockito.doReturn(cacheRegister).`when`(trafficRouter).getCacheRegister()
        val js: JsonNode? = JsonNodeFactory.instance.objectNode().put("ecsEnable", true)
        PowerMockito.`when`(cacheRegister.getConfig()).thenReturn(js)
        val m_an: Name?
        val m_host: Name?
        val m_admin: Name?
        m_an = Name.fromString("dns1.example.com.")
        m_host = Name.fromString("dns1.example.com.")
        m_admin = Name.fromString("admin.example.com.")
        ar = SOARecord(
            m_an, DClass.IN, 0x13A8,
            m_host, m_admin, 0xABCDEF12L, 0xCDEF1234L,
            0xEF123456L, 0x12345678L, 0x3456789AL
        )
        ns = NSRecord(m_an, DClass.IN, 12345L, m_an)
    }

    @Test
    @Throws(Exception::class)
    fun TestARecordQueryWithClientSubnetOption() {
        val name: Name? = Name.fromString("host1.example.com.")
        val question: Record? = Record.newRecord(name, Type.A, DClass.IN, 12345L)
        val query: Message? = Message.newQuery(question)

        //Add opt record, with client subnet option.
        val nmask: Int = 28
        val ipaddr: InetAddress? = Inet4Address.getByName("192.168.33.0")
        val cso: ClientSubnetOption? = ClientSubnetOption(nmask, ipaddr)
        val cso_list: MutableList<ClientSubnetOption?>? = ArrayList(1)
        cso_list.add(cso)
        val opt: OPTRecord? = OPTRecord(1280, 0, 0, 0, cso_list)
        query.addRecord(opt, Section.ADDITIONAL)


        // Add ARecord Entry in the zone
        val resolvedAddress: InetAddress? = Inet4Address.getByName("192.168.8.9")
        val answer: Record? = ARecord(name, DClass.IN, 12345L, resolvedAddress)
        val records: Array<Record?>? = arrayOf(ar, ns, answer)
        val m_an: Name? = Name.fromString("dns1.example.com.")
        val zone: Zone? = Zone(m_an, records)
        val builder: DNSAccessRecord.Builder? = DNSAccessRecord.Builder(1L, client)
        nameServer.setTrafficRouterManager(trafficRouterManager)
        nameServer.setEcsEnable(
            JsonUtils.optBoolean(
                trafficRouter.getCacheRegister().getConfig(),
                "ecsEnable",
                false
            )
        ) // this mimics what happens in ConfigHandler

        // Following is needed to mock this call: zone = trafficRouterManager.getTrafficRouter().getZone(qname, qtype, clientAddress, dnssecRequest, builder);
        PowerMockito.`when`(trafficRouterManager.getTrafficRouter()).thenReturn(trafficRouter)
        PowerMockito.`when`(
            trafficRouter.getZone(
                Matchers.any(Name::class.java), Matchers.any(
                    Int::class.javaPrimitiveType
                ), Matchers.eq(ipaddr), Matchers.any(
                    Boolean::class.javaPrimitiveType
                ), Matchers.any(
                    DNSAccessRecord.Builder::class.java
                )
            )
        ).thenReturn(zone)

        // The function call under test:
        val res: Message? = nameServer.query(query, client, builder)


        //Verification of response
        val qopt: OPTRecord? = res.getOPT()
        assert((qopt != null))
        var list: MutableList<EDNSOption?>? = Collections.EMPTY_LIST
        list = qopt.getOptions(EDNSOption.Code.CLIENT_SUBNET)
        assert((list !== Collections.EMPTY_LIST))
        val option: ClientSubnetOption? = list.get(0) as ClientSubnetOption?
        MatcherAssert.assertThat(nmask, org.hamcrest.Matchers.equalTo(option.getSourceNetmask()))
        MatcherAssert.assertThat(nmask, org.hamcrest.Matchers.equalTo(option.getScopeNetmask()))
        MatcherAssert.assertThat(ipaddr, org.hamcrest.Matchers.equalTo(option.getAddress()))
        nameServer.setEcsEnable(false)
    }

    @Test
    @Throws(Exception::class)
    fun TestARecordQueryWithMultipleClientSubnetOption() {
        val name: Name? = Name.fromString("host1.example.com.")
        val question: Record? = Record.newRecord(name, Type.A, DClass.IN, 12345L)
        val query: Message? = Message.newQuery(question)

        //Add opt record, with multiple client subnet option.
        val nmask1: Int = 16
        val nmask2: Int = 24
        val ipaddr1: InetAddress? = Inet4Address.getByName("192.168.0.0")
        val ipaddr2: InetAddress? = Inet4Address.getByName("192.168.33.0")
        val cso1: ClientSubnetOption? = ClientSubnetOption(nmask1, ipaddr1)
        val cso2: ClientSubnetOption? = ClientSubnetOption(nmask2, ipaddr2)
        val cso_list: MutableList<ClientSubnetOption?>? = ArrayList(1)
        cso_list.add(cso1)
        cso_list.add(cso2)
        val opt: OPTRecord? = OPTRecord(1280, 0, 0, 0, cso_list)
        query.addRecord(opt, Section.ADDITIONAL)


        // Add ARecord Entry in the zone
        val resolvedAddress: InetAddress? = Inet4Address.getByName("192.168.8.9")
        val answer: Record? = ARecord(name, DClass.IN, 12345L, resolvedAddress)
        val records: Array<Record?>? = arrayOf(ar, ns, answer)
        val m_an: Name? = Name.fromString("dns1.example.com.")
        val zone: Zone? = Zone(m_an, records)
        val builder: DNSAccessRecord.Builder? = DNSAccessRecord.Builder(1L, client)
        nameServer.setTrafficRouterManager(trafficRouterManager)
        nameServer.setEcsEnable(
            JsonUtils.optBoolean(
                trafficRouter.getCacheRegister().getConfig(),
                "ecsEnable",
                false
            )
        ) // this mimics what happens in ConfigHandler

        // Following is needed to mock this call: zone = trafficRouterManager.getTrafficRouter().getZone(qname, qtype, clientAddress, dnssecRequest, builder);
        PowerMockito.`when`(trafficRouterManager.getTrafficRouter()).thenReturn(trafficRouter)
        PowerMockito.`when`(
            trafficRouter.getZone(
                Matchers.any(Name::class.java), Matchers.any(
                    Int::class.javaPrimitiveType
                ), Matchers.eq(ipaddr2), Matchers.any(
                    Boolean::class.javaPrimitiveType
                ), Matchers.any(
                    DNSAccessRecord.Builder::class.java
                )
            )
        ).thenReturn(zone)

        // The function call under test:
        val res: Message? = nameServer.query(query, client, builder)


        //Verification of response
        val qopt: OPTRecord? = res.getOPT()
        assert((qopt != null))
        var list: MutableList<EDNSOption?>? = Collections.EMPTY_LIST
        list = qopt.getOptions(EDNSOption.Code.CLIENT_SUBNET)
        assert((list !== Collections.EMPTY_LIST))
        val option: ClientSubnetOption? = list.get(0) as ClientSubnetOption?
        MatcherAssert.assertThat(1, org.hamcrest.Matchers.equalTo(list.size))
        MatcherAssert.assertThat(nmask2, org.hamcrest.Matchers.equalTo(option.getSourceNetmask()))
        MatcherAssert.assertThat(nmask2, org.hamcrest.Matchers.equalTo(option.getScopeNetmask()))
        MatcherAssert.assertThat(ipaddr2, org.hamcrest.Matchers.equalTo(option.getAddress()))
        nameServer.setEcsEnable(false)
    }

    @Test
    @Throws(Exception::class)
    fun TestDeliveryServiceARecordQueryWithClientSubnetOption() {
        val cacheRegister: CacheRegister? = Mockito.mock(CacheRegister::class.java)
        Mockito.doReturn(cacheRegister).`when`(trafficRouter).getCacheRegister()
        val js: JsonNode? = JsonNodeFactory.instance.objectNode().put("ecsEnable", false)
        PowerMockito.`when`(cacheRegister.getConfig()).thenReturn(js)
        val node: ObjectNode? = JsonNodeFactory.instance.objectNode()
        val domainNode: ArrayNode? = node.putArray("domains")
        domainNode.add("example.com")
        node.put("routingName", "edge")
        node.put("coverageZoneOnly", false)
        val ds1: DeliveryService? = DeliveryService("ds1", node)
        val dses: MutableSet<*>? = HashSet<Any?>()
        dses.add(ds1)
        nameServer.setEcsEnabledDses(dses)
        val name: Name? = Name.fromString("host1.example.com.")
        val question: Record? = Record.newRecord(name, Type.A, DClass.IN, 12345L)
        val query: Message? = Message.newQuery(question)

        //Add opt record, with client subnet option.
        val nmask: Int = 28
        val ipaddr: InetAddress? = Inet4Address.getByName("192.168.33.0")
        val cso: ClientSubnetOption? = ClientSubnetOption(nmask, ipaddr)
        val cso_list: MutableList<ClientSubnetOption?>? = ArrayList(1)
        cso_list.add(cso)
        val opt: OPTRecord? = OPTRecord(1280, 0, 0, 0, cso_list)
        query.addRecord(opt, Section.ADDITIONAL)


        // Add ARecord Entry in the zone
        val resolvedAddress: InetAddress? = Inet4Address.getByName("192.168.8.9")
        val answer: Record? = ARecord(name, DClass.IN, 12345L, resolvedAddress)
        val records: Array<Record?>? = arrayOf(ar, ns, answer)
        val m_an: Name? = Name.fromString("dns1.example.com.")
        val zone: Zone? = Zone(m_an, records)
        val builder: DNSAccessRecord.Builder? = DNSAccessRecord.Builder(1L, client)
        nameServer.setTrafficRouterManager(trafficRouterManager)
        nameServer.setEcsEnable(
            JsonUtils.optBoolean(
                trafficRouter.getCacheRegister().getConfig(),
                "ecsEnable",
                false
            )
        ) // this mimics what happens in ConfigHandler

        // Following is needed to mock this call: zone = trafficRouterManager.getTrafficRouter().getZone(qname, qtype, clientAddress, dnssecRequest, builder);
        PowerMockito.`when`(trafficRouterManager.getTrafficRouter()).thenReturn(trafficRouter)
        PowerMockito.`when`(
            trafficRouter.getZone(
                Matchers.any(Name::class.java), Matchers.any(
                    Int::class.javaPrimitiveType
                ), Matchers.eq(ipaddr), Matchers.any(
                    Boolean::class.javaPrimitiveType
                ), Matchers.any(
                    DNSAccessRecord.Builder::class.java
                )
            )
        ).thenReturn(zone)

        // The function call under test:
        val res: Message? = nameServer.query(query, client, builder)


        //Verification of response
        val qopt: OPTRecord? = res.getOPT()
        assert((qopt != null))
        var list: MutableList<EDNSOption?>? = Collections.EMPTY_LIST
        list = qopt.getOptions(EDNSOption.Code.CLIENT_SUBNET)
        assert((list !== Collections.EMPTY_LIST))
        val option: ClientSubnetOption? = list.get(0) as ClientSubnetOption?
        MatcherAssert.assertThat(nmask, org.hamcrest.Matchers.equalTo(option.getSourceNetmask()))
        MatcherAssert.assertThat(nmask, org.hamcrest.Matchers.equalTo(option.getScopeNetmask()))
        MatcherAssert.assertThat(ipaddr, org.hamcrest.Matchers.equalTo(option.getAddress()))
        nameServer.setEcsEnable(false)
    }

    @Test
    @Throws(Exception::class)
    fun TestDeliveryServiceARecordQueryWithMultipleClientSubnetOption() {
        val cacheRegister: CacheRegister? = Mockito.mock(CacheRegister::class.java)
        Mockito.doReturn(cacheRegister).`when`(trafficRouter).getCacheRegister()
        val js: JsonNode? = JsonNodeFactory.instance.objectNode().put("ecsEnable", false)
        PowerMockito.`when`(cacheRegister.getConfig()).thenReturn(js)
        val name: Name? = Name.fromString("host1.example.com.")
        val question: Record? = Record.newRecord(name, Type.A, DClass.IN, 12345L)
        val query: Message? = Message.newQuery(question)
        val node: ObjectNode? = JsonNodeFactory.instance.objectNode()
        val domainNode: ArrayNode? = node.putArray("domains")
        domainNode.add("example.com")
        node.put("routingName", "edge")
        node.put("coverageZoneOnly", false)
        val ds1: DeliveryService? = DeliveryService("ds1", node)
        val dses: MutableSet<*>? = HashSet<Any?>()
        dses.add(ds1)
        nameServer.setEcsEnabledDses(dses)


        //Add opt record, with multiple client subnet option.
        val nmask1: Int = 16
        val nmask2: Int = 24
        val ipaddr1: InetAddress? = Inet4Address.getByName("192.168.0.0")
        val ipaddr2: InetAddress? = Inet4Address.getByName("192.168.33.0")
        val cso1: ClientSubnetOption? = ClientSubnetOption(nmask1, ipaddr1)
        val cso2: ClientSubnetOption? = ClientSubnetOption(nmask2, ipaddr2)
        val cso_list: MutableList<ClientSubnetOption?>? = ArrayList(1)
        cso_list.add(cso1)
        cso_list.add(cso2)
        val opt: OPTRecord? = OPTRecord(1280, 0, 0, 0, cso_list)
        query.addRecord(opt, Section.ADDITIONAL)


        // Add ARecord Entry in the zone
        val resolvedAddress: InetAddress? = Inet4Address.getByName("192.168.8.9")
        val answer: Record? = ARecord(name, DClass.IN, 12345L, resolvedAddress)
        val records: Array<Record?>? = arrayOf(ar, ns, answer)
        val m_an: Name? = Name.fromString("dns1.example.com.")
        val zone: Zone? = Zone(m_an, records)
        val builder: DNSAccessRecord.Builder? = DNSAccessRecord.Builder(1L, client)
        nameServer.setTrafficRouterManager(trafficRouterManager)
        nameServer.setEcsEnable(
            JsonUtils.optBoolean(
                trafficRouter.getCacheRegister().getConfig(),
                "ecsEnable",
                false
            )
        ) // this mimics what happens in ConfigHandler

        // Following is needed to mock this call: zone = trafficRouterManager.getTrafficRouter().getZone(qname, qtype, clientAddress, dnssecRequest, builder);
        PowerMockito.`when`(trafficRouterManager.getTrafficRouter()).thenReturn(trafficRouter)
        PowerMockito.`when`(
            trafficRouter.getZone(
                Matchers.any(Name::class.java), Matchers.any(
                    Int::class.javaPrimitiveType
                ), Matchers.eq(ipaddr2), Matchers.any(
                    Boolean::class.javaPrimitiveType
                ), Matchers.any(
                    DNSAccessRecord.Builder::class.java
                )
            )
        ).thenReturn(zone)

        // The function call under test:
        val res: Message? = nameServer.query(query, client, builder)


        //Verification of response
        val qopt: OPTRecord? = res.getOPT()
        assert((qopt != null))
        var list: MutableList<EDNSOption?>? = Collections.EMPTY_LIST
        list = qopt.getOptions(EDNSOption.Code.CLIENT_SUBNET)
        assert((list !== Collections.EMPTY_LIST))
        val option: ClientSubnetOption? = list.get(0) as ClientSubnetOption?
        MatcherAssert.assertThat(1, org.hamcrest.Matchers.equalTo(list.size))
        MatcherAssert.assertThat(nmask2, org.hamcrest.Matchers.equalTo(option.getSourceNetmask()))
        MatcherAssert.assertThat(nmask2, org.hamcrest.Matchers.equalTo(option.getScopeNetmask()))
        MatcherAssert.assertThat(ipaddr2, org.hamcrest.Matchers.equalTo(option.getAddress()))
        nameServer.setEcsEnable(false)
    }
}