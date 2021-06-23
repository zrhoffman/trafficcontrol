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
package secure

import com.comcast.cdn.traffic_control.traffic_router.core.router.TrafficRouter.getCoverageZoneCacheLocation
import com.comcast.cdn.traffic_control.traffic_router.core.router.TrafficRouter.selectCachesByCZ
import com.comcast.cdn.traffic_control.traffic_router.core.router.TrafficRouter.consistentHashForCoverageZone
import com.comcast.cdn.traffic_control.traffic_router.core.router.TrafficRouter.consistentHashForGeolocation
import com.comcast.cdn.traffic_control.traffic_router.core.router.TrafficRouter.consistentHashDeliveryService
import com.comcast.cdn.traffic_control.traffic_router.core.router.TrafficRouter.buildPatternBasedHashString
import com.comcast.cdn.traffic_control.traffic_router.core.router.TrafficRouter.buildPatternBasedHashStringDeliveryService
import com.comcast.cdn.traffic_control.traffic_router.core.router.TrafficRouter.consistentHashSteeringForCoverageZone
import com.comcast.cdn.traffic_control.traffic_router.core.router.TrafficRouter.cacheRegister
import com.comcast.cdn.traffic_control.traffic_router.core.router.TrafficRouter.getZone
import com.comcast.cdn.traffic_control.traffic_router.core.router.TrafficRouter.isDnssecZoneDiffingEnabled
import com.comcast.cdn.traffic_control.traffic_router.core.router.TrafficRouter.isEdgeHTTPRouting
import com.comcast.cdn.traffic_control.traffic_router.core.router.TrafficRouter.selectTrafficRoutersMiss
import com.comcast.cdn.traffic_control.traffic_router.core.router.TrafficRouter.selectTrafficRoutersLocalized
import com.comcast.cdn.traffic_control.traffic_router.core.router.TrafficRouter.isConsistentDNSRouting
import com.comcast.cdn.traffic_control.traffic_router.core.router.TrafficRouter.inetRecordsFromCaches
import com.comcast.cdn.traffic_control.traffic_router.core.router.TrafficRouter.isEdgeDNSRouting
import com.comcast.cdn.traffic_control.traffic_router.core.router.TrafficRouter.route
import com.comcast.cdn.traffic_control.traffic_router.core.router.TrafficRouter.anonymousIpDatabaseService
import com.comcast.cdn.traffic_control.traffic_router.core.router.TrafficRouter.getClientGeolocation
import com.comcast.cdn.traffic_control.traffic_router.core.router.TrafficRouter.requestHeaders
import com.comcast.cdn.traffic_control.traffic_router.core.router.TrafficRouter.getLocation
import com.comcast.cdn.traffic_control.traffic_router.core.router.TrafficRouter.getDeepCoverageZoneLocationByIP
import com.comcast.cdn.traffic_control.traffic_router.core.router.TrafficRouter.zoneManager
import com.comcast.cdn.traffic_control.traffic_router.core.router.TrafficRouter.configurationChanged
import com.comcast.cdn.traffic_control.traffic_router.core.router.TrafficRouter.setState
import com.comcast.cdn.traffic_control.traffic_router.core.router.TrafficRouter.setSteeringRegistry
import com.comcast.cdn.traffic_control.traffic_router.core.router.TrafficRouter.setApplicationContext
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
import com.comcast.cdn.traffic_control.traffic_router.core.router.RouteResult
import org.springframework.context.ApplicationListener
import org.springframework.context.event.ContextRefreshedEvent
import com.comcast.cdn.traffic_control.traffic_router.core.secure.CertificatesClient
import com.comcast.cdn.traffic_control.traffic_router.core.secure.CertificatesResponse
import com.comcast.cdn.traffic_control.traffic_router.configuration.ConfigurationListener
import org.springframework.core.env.Environment
import javax.management.ObjectName
import com.comcast.cdn.traffic_control.traffic_router.shared.DeliveryServiceCertificatesMBean
import org.springframework.context.event.ApplicationContextEvent
import com.comcast.cdn.traffic_control.traffic_router.core.monitor.TrafficMonitorResourceUrl
import org.springframework.context.event.ContextClosedEvent
import java.security.spec.KeySpec
import java.security.Security
import org.bouncycastle.jce.provider.BouncyCastleProvider
import com.comcast.cdn.traffic_control.traffic_router.secure.Pkcs
import com.comcast.cdn.traffic_control.traffic_router.secure.Pkcs1
import com.comcast.cdn.traffic_control.traffic_router.secure.Pkcs1KeySpecDecoder
import com.comcast.cdn.traffic_control.traffic_router.secure.Pkcs8
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.RSAPrivateCrtKeySpec
import org.bouncycastle.asn1.ASN1Sequence
import org.bouncycastle.asn1.ASN1SequenceParser
import org.bouncycastle.asn1.ASN1Integer
import java.security.spec.RSAPublicKeySpec
import com.comcast.cdn.traffic_control.traffic_router.shared.SigningData
import java.security.KeyPairGenerator
import com.comcast.cdn.traffic_control.traffic_router.shared.ZoneTestRecords
import com.comcast.cdn.traffic_control.traffic_router.shared.IsEqualCollection
import javax.management.NotificationBroadcasterSupport
import javax.management.AttributeChangeNotification
import com.comcast.cdn.traffic_control.traffic_router.shared.DeliveryServiceCertificates
import org.junit.runner.RunWith
import org.powermock.modules.junit4.PowerMockRunner
import org.powermock.core.classloader.annotations.PrepareForTest
import org.junit.Before
import sun.security.rsa.RSAPrivateCrtKeyImpl
import org.powermock.api.mockito.PowerMockito
import java.lang.System
import com.comcast.cdn.traffic_control.traffic_router.utils.HttpsProperties
import java.nio.file.Paths
import javax.net.ssl.X509ExtendedKeyManager
import javax.net.ssl.X509KeyManager
import com.comcast.cdn.traffic_control.traffic_router.secure.CertificateRegistry
import java.security.Principal
import java.lang.UnsupportedOperationException
import javax.net.ssl.SSLEngine
import javax.net.ssl.ExtendedSSLSession
import javax.net.ssl.SNIServerName
import com.comcast.cdn.traffic_control.traffic_router.secure.HandshakeData
import com.comcast.cdn.traffic_control.traffic_router.secure.CertificateDecoder
import com.comcast.cdn.traffic_control.traffic_router.secure.CertificateDataConverter
import kotlin.jvm.Volatile
import com.comcast.cdn.traffic_control.traffic_router.protocol.RouterNioEndpoint
import java.security.KeyStore
import com.comcast.cdn.traffic_control.traffic_router.secure.CertificateRegistry.CertificateRegistryHolder
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.asn1.x509.BasicConstraints
import org.bouncycastle.asn1.x509.ExtendedKeyUsage
import org.bouncycastle.asn1.x509.KeyPurposeId
import org.bouncycastle.cert.X509CertificateHolder
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import javax.management.NotificationListener
import com.comcast.cdn.traffic_control.traffic_router.secure.CertificateDataListener
import com.comcast.cdn.traffic_control.traffic_router.secure.PrivateKeyDecoder
import org.bouncycastle.jcajce.provider.asymmetric.rsa.BCRSAPrivateCrtKey
import org.apache.catalina.LifecycleListener
import org.apache.catalina.LifecycleEvent
import com.comcast.cdn.traffic_control.traffic_router.tomcat.TomcatLifecycleListener
import javax.management.MBeanServer
import com.comcast.cdn.traffic_control.traffic_router.protocol.RouterProtocolHandler
import com.comcast.cdn.traffic_control.traffic_router.protocol.LanguidPoller
import org.apache.tomcat.util.net.SSLHostConfigCertificate
import org.apache.tomcat.util.net.SSLUtilBase
import com.comcast.cdn.traffic_control.traffic_router.protocol.RouterSslUtil
import org.apache.tomcat.util.net.openssl.OpenSSLEngine
import org.apache.tomcat.util.net.openssl.OpenSSLContext
import javax.net.ssl.SSLSessionContext
import org.apache.coyote.http11.Http11NioProtocol
import com.comcast.cdn.traffic_control.traffic_router.protocol.LanguidProtocol
import org.apache.tomcat.util.net.NioEndpoint
import org.apache.tomcat.util.net.SSLHostConfig
import org.apache.tomcat.util.net.SocketWrapperBase
import org.apache.tomcat.util.net.NioChannel
import org.apache.tomcat.util.net.SocketEvent
import org.apache.tomcat.util.net.SocketProcessorBase
import com.comcast.cdn.traffic_control.traffic_router.protocol.RouterNioEndpoint.RouterSocketProcessor
import org.apache.tomcat.jni.SSL
import org.apache.coyote.http11.AbstractHttp11JsseProtocol
import com.comcast.cdn.traffic_control.traffic_router.protocol.LanguidNioProtocol
import java.lang.ClassNotFoundException
import org.apache.coyote.ProtocolHandler
import org.apache.tomcat.util.net.SSLImplementation
import org.apache.tomcat.util.net.SSLSupport
import org.apache.tomcat.util.net.jsse.JSSESupport
import org.apache.tomcat.util.net.SSLUtil
import secure.KeyManagerTest.TestSNIServerName
import java.lang.management.ManagementFactory
import secure.CertificateDataConverterTest
import com.comcast.cdn.traffic_control.traffic_router.protocol.RouterSslImplementation
import com.comcast.cdn.traffic_control.traffic_router.shared.Certificate
import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import org.hamcrest.MatcherAssert
import org.hamcrest.Matchers
import org.junit.Assert
import org.junit.Test
import java.lang.Exception
import java.util.*

class CertificateDataConverterTest constructor() {
    private var certificateDataConverter: CertificateDataConverter? = null
    private var certificateData: CertificateData? = null
    private val certDate: Date? = null
    @Before
    @PrepareForTest(Instant::class)
    @Throws(Exception::class)
    fun before() {
        val certificate: Certificate? = Certificate()
        certificate.setCrt("encodedchaindata")
        certificate.setKey("encodedkeydata")
        certificateData = CertificateData()
        certificateData.setCertificate(certificate)
        certificateData.setDeliveryservice("some-delivery-service")
        certificateData.setHostname("example.com")
        certificateDataConverter = CertificateDataConverter()
    }

    @Test
    @Throws(Exception::class)
    fun itConvertsValidCertToHandshakeData() {
        try {
            certificateData = (ObjectMapper().readValue<Any?>(
                VALID_CERT_DATA,
                object : TypeReference<CertificateData?>() {}) as CertificateData?)
        } catch (e: Exception) {
            Assert.fail("Failed parsing json data: " + e.message)
        }
        val handshakeData: HandshakeData? = certificateDataConverter.toHandshakeData(certificateData)
        MatcherAssert.assertThat(handshakeData, Matchers.notNullValue())
        MatcherAssert.assertThat(
            handshakeData.getDeliveryService(),
            Matchers.equalTo(certificateData.getDeliveryservice())
        )
        MatcherAssert.assertThat(handshakeData.getHostname(), Matchers.equalTo(certificateData.getHostname()))
    }

    @Test
    @Throws(Exception::class)
    fun itRejectsExpiredCert() {
        try {
            certificateData = (ObjectMapper().readValue<Any?>(
                EXPIRED_CERT_DATA,
                object : TypeReference<CertificateData?>() {}) as CertificateData?)
        } catch (e: Exception) {
            Assert.fail("Failed parsing json data: " + e.message)
        }
        val handshakeData: HandshakeData? = certificateDataConverter.toHandshakeData(certificateData)
        MatcherAssert.assertThat(handshakeData, Matchers.nullValue())
    }

    @Test
    @Throws(Exception::class)
    fun itRejectsModulusMismatch() {
        try {
            certificateData = (ObjectMapper().readValue<Any?>(
                MOD_MISS_CERT_DATA,
                object : TypeReference<CertificateData?>() {}) as CertificateData?)
        } catch (e: Exception) {
            Assert.fail("Failed parsing json data: " + e.message)
        }
        val handshakeData: HandshakeData? = certificateDataConverter.toHandshakeData(certificateData)
        MatcherAssert.assertThat(handshakeData, Matchers.nullValue())
    }

    @Test
    @Throws(Exception::class)
    fun itRejectsSubjectMismatch() {
        try {
            certificateData = (ObjectMapper().readValue<Any?>(
                SUBJECT_MISS_CERT_DATA,
                object : TypeReference<CertificateData?>() {}) as CertificateData?)
        } catch (e: Exception) {
            Assert.fail("Failed parsing json data: " + e.message)
        }
        val handshakeData: HandshakeData? = certificateDataConverter.toHandshakeData(certificateData)
        MatcherAssert.assertThat(handshakeData, Matchers.nullValue())
    }

    @Test
    @Throws(Exception::class)
    fun itAcceptsSubjectAlternateNames() {
        try {
            certificateData = (ObjectMapper().readValue<Any?>(
                VALID_SAN_CERT_DATA,
                object : TypeReference<CertificateData?>() {}) as CertificateData?)
        } catch (e: Exception) {
            Assert.fail("Failed parsing json data: " + e.message)
        }
        val handshakeData: HandshakeData? = certificateDataConverter.toHandshakeData(certificateData)
        MatcherAssert.assertThat(handshakeData, Matchers.notNullValue())
        MatcherAssert.assertThat(
            handshakeData.getDeliveryService(),
            Matchers.equalTo(certificateData.getDeliveryservice())
        )
        MatcherAssert.assertThat(handshakeData.getHostname(), Matchers.equalTo(certificateData.getHostname()))
    }

    @Test
    @Throws(Exception::class)
    fun itAcceptsWildcardSubjectAlternateNames() {
        try {
            certificateData = (ObjectMapper().readValue<Any?>(
                VALID_SAN_WILDCARD_CERT_DATA,
                object : TypeReference<CertificateData?>() {}) as CertificateData?)
        } catch (e: Exception) {
            Assert.fail("Failed parsing json data: " + e.message)
        }
        val handshakeData: HandshakeData? = certificateDataConverter.toHandshakeData(certificateData)
        MatcherAssert.assertThat(handshakeData, Matchers.notNullValue())
        MatcherAssert.assertThat(
            handshakeData.getDeliveryService(),
            Matchers.equalTo(certificateData.getDeliveryservice())
        )
        MatcherAssert.assertThat(handshakeData.getHostname(), Matchers.equalTo(certificateData.getHostname()))
    }

    companion object {
        // Remember, the key and crt fields are Base64 encoded versions of the cert and key
        private val SUBJECT_MISS_CERT_DATA: String? = ("    {\n" +
                "      \"deliveryservice\": \"https-subject-miss\",\n" +
                "      \"certificate\": {\n" +
                "        \"comment\" : \"The following is a self-signed key for *.subject-miss.thecdn.example.com\",\n" +
                "        \"key\": \"LS0tLS1CRUdJTiBQUklWQVRFIEtFWS0tLS0tCk1JSUV2QUlCQURBTkJna3Foa2lHOXcwQkFRRUZBQVNDQktZd2dnU2lBZ0VBQW9JQkFRQzhBWVVFYk1YcHZiVUMKaDBrNWRxYURnTHJGL3Y5VDdtOFNLUnVuRldYYUhFalVvcWlZc29tekhuZjNyUkVNRWpkVXB0M0lCVzk3M090cApqNmlkNUNLTHlFVDNUQ3h2ZHNERzhiYXB3UEdNT0dzQWhTMGxucmlrRll6ejArZXpxMWhzczcxRDBqN3o1TzlLCmxPVUJxSUgzOG16YU1JaFN3VXpsSGdFRzJjdlJiK1RwajhpU0k3Z3psek8rMVM1OEExS21UbjVDMC9ia0lvcFYKREJ5V3FySmpqSXZuWjBvK2I1MkRMcExzdlVnRU5BOVdHRzkycG8wS0RDZnFmNjN0RW5oRGYvZStFT0o5NUs5UQpCUG45YW82OVJaM0V3cDk5bnlveDJ6cmtHLzcvMTVIV3Z5aUVzQUR2TWxNaTg4bTJRTzBOaDA5ZWlrWWFRWDlVCkUzbTM4VDVkQWdNQkFBRUNnZ0VBT2UxNTc4Z1lIeElkMEw2Z2VEMHZ4enNGMFhYbGRCWDJVVEVyWFFzQnkvZUYKRlVkZERWZU5pQXd1U0xraGxJZVVWdGZuWS9jUXg2aGxQS3hQOXY1UkNxTFZaU0VxVzluS1FrSTkxd1lsSnVCSApUK3k0NFd1TFZydHhKN3UyRzYwQzNOTncwSkhhWmNtM1ZWS1ZVVEo3Z1V0SDhONmRVbXBPNkJXYm1XSElKQ3AvCnRjL29QVTZzTWc2RGh2MVFxeUpJeHQ5MWRmSnpBZVdkV01MM3ZmNnRVUDF6bTh2M2g0WXBZSGR5LzZBMzZjZkQKa0xnZkkybktkVEhLUDBldlZFS3M5L3hQWVlxQWVyTnlCV2NFWCtjVy93ZzVSVUVrT2lpajZUY0h1cmVnV09VbQp5cWlCOFNoQWVwdEtnN0VVaHZ2V2ZLSEtMTmJURUV5UE5GOXVPR1VqL1FLQmdRRGZUdy9IbS9oUDJYZUFGeEhZCnViUTBIN2xHQWxLZEhydTIvVXlFK3d0WDlEYXU1UTZRVzVkVzJJMTdkOG5TL2taUkloQnhSM08rMGMxN3VUaHoKWDFseWtmT3ZZb3NlSGhhN0IrN21rL2RVTkJZYVJ1UG1IcEEvTkx6ekI3OWdhU2JPVk9lOVQ5cVovb2lndlRXYQp0TG0rOFIzeVhyUktOdUZtNk1kbnFYdSswd0tCZ1FEWGgybU5EQW9sMHJlcFY0MnYvaSt1QmF4bkozZlJVU284ClpkUk5GczVWTFlxUnh0c1BubkNwcTh3OWtKK2paZWNWTkRNelB3dmpBdUdqYXkyUHVRclF5MEUyckk5RExITEIKTVVxVHJENHBSN0NrK3VFVGQ3ZlBEOHNRdEJmUkp6amdBa3pvWUk1djZ1dzlpUFF6U2tzT2d5WFlNV0ZUU1ZJNwplVVhHWDRDd0R3S0JnRjduemhBS2xKenpFcHVvc2xnR2pMUythdEo3T0RzNGpaVDIwQ2VRUGtEeU5LOWVBRE9RCkNhREtSazhjR1BXSVJjQkRsdk5kNTY1SW9ta2J6Z2NTbGdSZ1RVM1R0c1psQ1VvUjFCSEEveE9WVTNOMWYzUVUKdHo5MW5YdzRaYmlHMkF4Ry8zcHd6cm8xK0VGQVNPRG9RQzBMY3F2SVhoMVFkN2x4NHhXR2JXWXJBb0dBQjhZKwpySFBPdWVhTDhYUFRESklpcmloT083cFV2Qnd0WmRoV2ZDRmlkL2dZazRHVXpVOXR5UEVGZ1FNQ2Z5WmgyNFh5Cmd0cTNWd3ozanFtRER6Z2hoNzZOTDZleDB6NTdOVFROOTkyeXNGS0JzTEhNQktQQTRaczBPL29ERWV4VVJPQlEKWGVGOXdkTzdpY3l5NGxhL3RscE10eXV3MHd4R0J4Y3N5U2NRd1VrQ2dZQng0UGk4NkhmWkVnT3p4bU9NYkhGTApmeFFWbXpZL0Y3eCsyOGMrQWQ3VVZRMVVjcDRUdEdMT1pHUGRIYnZzR2dZeXY4cSs0MTcwK1M0YkY4bC9JRThnCjJBNzl6VzNjMjhVNzR0KytxQ0p4bS82SmxtR3RCQkt0Mk9ZSE5ocUdRQ2Z4Y0krWGFPQUgyUUFNSS9zZ3JzOXQKM3dZNlY2VUQ2K1lCTDVFRFp6T2NMQT09Ci0tLS0tRU5EIFBSSVZBVEUgS0VZLS0tLS0K\",\n" +
                "        \"crt\": \"LS0tLS1CRUdJTiBDRVJUSUZJQ0FURS0tLS0tCk1JSURvakNDQW9vQ0NRQ1JFTVdLWEhZYkhUQU5CZ2txaGtpRzl3MEJBUXNGQURDQmtURUxNQWtHQTFVRUJoTUMKVlZNeEN6QUpCZ05WQkFnTUFrTlBNUXd3Q2dZRFZRUUhEQU5FUlU0eEN6QUpCZ05WQkFvTUFsUkRNUXN3Q1FZRApWUVFMREFKVVF6RXFNQ2dHQTFVRUF3d2hLaTV6ZFdKcVpXTjBMVzFwYzNNdWRHaGxZMlJ1TG1WNFlXMXdiR1V1ClkyOXRNU0V3SHdZSktvWklodmNOQVFrQkZoSjBZMEJ6Wld4bUxYTnBaMjVsWkM1amIyMHdJQmNOTVRrd016QTEKTVRjME1ETXhXaGdQTWpFeE9UQXlNRGt4TnpRd016RmFNSUdSTVFzd0NRWURWUVFHRXdKVlV6RUxNQWtHQTFVRQpDQXdDUTA4eEREQUtCZ05WQkFjTUEwUkZUakVMTUFrR0ExVUVDZ3dDVkVNeEN6QUpCZ05WQkFzTUFsUkRNU293CktBWURWUVFERENFcUxuTjFZbXBsWTNRdGJXbHpjeTUwYUdWalpHNHVaWGhoYlhCc1pTNWpiMjB4SVRBZkJna3EKaGtpRzl3MEJDUUVXRW5SalFITmxiR1l0YzJsbmJtVmtMbU52YlRDQ0FTSXdEUVlKS29aSWh2Y05BUUVCQlFBRApnZ0VQQURDQ0FRb0NnZ0VCQUx3QmhRUnN4ZW05dFFLSFNUbDJwb09BdXNYKy8xUHVieElwRzZjVlpkb2NTTlNpCnFKaXlpYk1lZC9ldEVRd1NOMVNtM2NnRmIzdmM2Mm1QcUoza0lvdklSUGRNTEc5MndNYnh0cW5BOFl3NGF3Q0YKTFNXZXVLUVZqUFBUNTdPcldHeXp2VVBTUHZQazcwcVU1UUdvZ2ZmeWJOb3dpRkxCVE9VZUFRYlp5OUZ2NU9tUAp5SklqdURPWE03N1ZMbndEVXFaT2ZrTFQ5dVFpaWxVTUhKYXFzbU9NaStkblNqNXZuWU11a3V5OVNBUTBEMVlZCmIzYW1qUW9NSitwL3JlMFNlRU4vOTc0UTRuM2tyMUFFK2YxcWpyMUZuY1RDbjMyZktqSGJPdVFiL3YvWGtkYS8KS0lTd0FPOHlVeUx6eWJaQTdRMkhUMTZLUmhwQmYxUVRlYmZ4UGwwQ0F3RUFBVEFOQmdrcWhraUc5dzBCQVFzRgpBQU9DQVFFQXJBTWNyZWY0bTNVTlNSRU54dW0xWTNXYlgzWU91VTdLVyt4bEV2UGVHLzFmYitFRUNrcjg5dXFZCm95OWFWTjYvK3RMTWd5Y1QxL1cxVnhSNkl5bFpZcE9SRFRVZ1c4L3ZvSUROVUZack1VekR6RmZNVlFwdUxyUzkKSk5kejk2aTFrNjdBMGRrdFBURjExam5DZEY2VVhKMHdZTmZCVEIvbXo1T2diWVdaQWsrYW5pTTR5NUwyb3ZaKwpLOEZUVi8wUHpIUWRVTkVGVjN4QzVRcVB3aW1oY3BrbDU1bzJnTjVUNXllVnBDekRYeHhSWWR0YjRUYmsvVDF5CnlBVkhQQ3ZFeENnbXcrUW5TS1VweXRKQ0NqM3pOS01FY1V5dkxFOUFNTmlrdFJpY2c4NG5UTk9hWWRnM1ZaUk8KMVRYY3ZNb0l5NFNOZUk0NEszTkhsQW1IdEJkY0VBPT0KLS0tLS1FTkQgQ0VSVElGSUNBVEUtLS0tLQo=\"\n" +
                "      },\n" +
                "      \"hostname\": \"*.https-subject-miss.thecdn.example.com\"\n" +
                "    }")
        private val VALID_CERT_DATA: String? = ("    {\n" +
                "      \"deliveryservice\": \"https-valid-test\",\n" +
                "      \"certificate\": {\n" +
                "        \"comment\" : \"The following is just a self signed certificate and key to use for testing this is NOT private data from a CA\",\n" +
                "        \"key\": " +
                "\"LS0tLS1CRUdJTiBQUklWQVRFIEtFWS0tLS0tCk1JSUV2Z0lCQURBTkJna3Foa2lHOXcwQkFRRUZBQVNDQktnd2dnU2tBZ0VBQW9JQkFRQzVEMVhNbXJiQy9CT1gKUkZMVkczbTNSbmhWZ0ZJdUQ5dXhWSEJDYXR2TEFuc2ZyalhCM2tyZjVNTDVuS3dZRWl3OCtkQWo2N1Z2QkR4cwpDMTYvbFFBbFM4YnBxT1NRbzU5T0RDcVBNZmZaYzVVazdVdjUzN0R5MWFHMjRiT1R0eUxjQzIxc2MxSm1YWHVjCkVlQlZUZldFWUVLdS9McHEvZDZZUlNsa1lXUUt2TDBmUzRja0FtcUJkRVk2Q0s3ajZyYnphZGJIVHB2SXdQWGgKWVNTOWlJOFQxKzRTYTZDOTljcnRUR2ZZb21BL2hFWVFPTnVSVk42VUl5c1Bob2RCVndsVTJEV1pYNndyZm5DZApNOFBCajNHSXVVMWVwV0RMUVZYa2cvdUxmZERaaU8xZ1p5UWhNN3V0ekE5WVMzK3VXaGtmczRUdFE1Q3ZVdEx1ClI0enlMVkQ3QWdNQkFBRUNnZ0VBYkd4a3EzeVZ5WVdoQU1aQjlhT2tXMUhKWE9iU3Z6UUJWbE1QZG9wZS9nRVYKSEFtWWExNk81Y0NFejNRUWpBWFJyMlA1bzZJTTZkOUVlMVRxRFRzQ0c5ZmEwYmxuT0tyMHdlaDA0dksyc010OApQV2RlVlNiTzZHZHIyTmRCdkREWEZxOEhURHdBc2dMaFVoNVRIZ2VQNmgvdjBkQTJkRXNMS0pHVTM4QUR1aG05ClpKRCthbm5KQ1BFVXNvMmtqeE40UjFHUmQwb2ZLSWFZeTg3dlhiK3FleXpsL1lreUEvYU9wMkh1S1RENmVpRGkKbTZrWUp6Q1k3ZEluYjlCNlZTTm52UlAzNyttM0JLSFlRaE5kbVJKQlp4c1pFQTRKaFYrQVYwNHlWUE1la3FoMgpqeVVxRFBEaGVMVW5FalJmc0FnOFVNU0JXQU5sLzkyNzNoR2FGeHQvTVFLQmdRRGVhNUhFd2oyVVBhcS9yMzdsCm90cFhBUU9qeEFLb2tyMWZNVTlaWksyTWdLY2hoSks0c1R0Uk5VbE03M2lDeHBPZTJTbW5ZeG5GcldrOVRkRjEKQ3habFJyVDBKZGxHOXJEMlNCSUR6b3FBaytWbHE5YzIxNE12NXdQZkpLZEpxaCtaVGZKdmZtNU9halhjeXFMZQpRSVRmdGVpdFRNWFJpQjBsaFpNSEo0RzVVd0tCZ1FEVS84OFo0UUxGa0xnWDMvMUMxcit4V3NuVUY5UGtHVXpSCm9USmg0enVXNHpwSkRDNitLS0lwckVZajIzdEdFUWxEZHJGcFBIWFZtMGVqV2QrRGN6THlmNFdZdWIxTTBuRksKbUpSaGhOMXhFRitNVmtPYjlWS2ZOU2xFUG16WHptcTlpdjVxUVlmZHJPLzhhQ0JMUW5UcUYveTJUUVVNN2tsNQpXbGo3Si9lTXVRS0JnUUNxTmJXNjFrN2JxQW1JWVp3QnpndTY4enErMDV5Wk5wcVhRNXdPcy80Zi9NQnA1Uk9ICkpaSllSaWdQS1YrVzdMSkJxTHk0clIwbTZ0c1RuLzYvekRsYVRhN2kvQ2YzcDRlcklXSXY2WnFTWlJ2ekgzczIKSzl6b0Jxa3UxZFR6aWE1ZTJvakNEQVlNR2ptWCtyYUMwT3NlYkE1Z3VOVFYwWTFFanFFQ281Z2hvd0tCZ1FDRQo5MnlCNjBXZnI4ZzhuMGVyQWdTSTR2UTd3dVF6OE5kVHhoMTluaTBFOUxUZUJRenBDTlN5enlpNkdibks4N2VrCnRlUHFuaU94UlU1ald5ZDlGOTBtSlJWeFVnSXFndlRXYkltMGx3em1HQ0tOcVF4cnY2bmtXWHQ1YnI3anVhaEkKeXd3bnFPRDRNWTFmTkdGMG1mZ0NheGNIZHUxQU5VRUkwSzNibkFlZGdRS0JnR1Y5VGxOSmtQZ2xma1A5dFRDTApldWtPVkNiTmNFc1dSRHYzMTFQa3pKemw3OHNRbENXL0NWK2NnK2JHOXB5ZEJJWUx3ZVRrUnp0d3FoVFE1L2JECmw1cWM1MVVKSkxJeEJ1TEIwZGJnMVh0eDF0VUdTaEV5UTFDc2U5SEwzKzgrbVRCN2drNDdpb2NzUEFuNXMxVnoKQ1ZjUVFQRnVmYWkrRzNwakI1Q0gvUEtnCi0tLS0tRU5EIFBSSVZBVEUgS0VZLS0tLS0K\",\n" +
                "        \"crt\": " +
                "\"LS0tLS1CRUdJTiBDRVJUSUZJQ0FURS0tLS0tCk1JSURzakNDQXBvQ0NRRHpibHduYzNBLzVqQU5CZ2txaGtpRzl3MEJBUXNGQURDQm1URUxNQWtHQTFVRUJoTUMKVlZNeEN6QUpCZ05WQkFnTUFrTlBNUXd3Q2dZRFZRUUhEQU5FUlU0eER6QU5CZ05WQkFvTUJrRndZV05vWlRFTApNQWtHQTFVRUN3d0NWRU14TGpBc0JnTlZCQU1NSlNvdWFIUjBjSE10ZG1Gc2FXUXRkR1Z6ZEM1MGFHVmpaRzR1ClpYaGhiWEJzWlM1amIyMHhJVEFmQmdrcWhraUc5dzBCQ1FFV0VuUmpRSE5sYkdZdGMybG5ibVZrTG1OdmJUQWcKRncweE9UQXpNRFV4TnpRMk1UTmFHQTh5TVRFNU1ESXdPVEUzTkRZeE0xb3dnWmt4Q3pBSkJnTlZCQVlUQWxWVApNUXN3Q1FZRFZRUUlEQUpEVHpFTU1Bb0dBMVVFQnd3RFJFVk9NUTh3RFFZRFZRUUtEQVpCY0dGamFHVXhDekFKCkJnTlZCQXNNQWxSRE1TNHdMQVlEVlFRRERDVXFMbWgwZEhCekxYWmhiR2xrTFhSbGMzUXVkR2hsWTJSdUxtVjQKWVcxd2JHVXVZMjl0TVNFd0h3WUpLb1pJaHZjTkFRa0JGaEowWTBCelpXeG1MWE5wWjI1bFpDNWpiMjB3Z2dFaQpNQTBHQ1NxR1NJYjNEUUVCQVFVQUE0SUJEd0F3Z2dFS0FvSUJBUUM1RDFYTW1yYkMvQk9YUkZMVkczbTNSbmhWCmdGSXVEOXV4VkhCQ2F0dkxBbnNmcmpYQjNrcmY1TUw1bkt3WUVpdzgrZEFqNjdWdkJEeHNDMTYvbFFBbFM4YnAKcU9TUW81OU9EQ3FQTWZmWmM1VWs3VXY1MzdEeTFhRzI0Yk9UdHlMY0MyMXNjMUptWFh1Y0VlQlZUZldFWUVLdQovTHBxL2Q2WVJTbGtZV1FLdkwwZlM0Y2tBbXFCZEVZNkNLN2o2cmJ6YWRiSFRwdkl3UFhoWVNTOWlJOFQxKzRTCmE2Qzk5Y3J0VEdmWW9tQS9oRVlRT051UlZONlVJeXNQaG9kQlZ3bFUyRFdaWDZ3cmZuQ2RNOFBCajNHSXVVMWUKcFdETFFWWGtnL3VMZmREWmlPMWdaeVFoTTd1dHpBOVlTMyt1V2hrZnM0VHRRNUN2VXRMdVI0enlMVkQ3QWdNQgpBQUV3RFFZSktvWklodmNOQVFFTEJRQURnZ0VCQUh0clNXMUE5em83K1NkTkM0RGVFeXdQOTZOeE95eDdobHNzCk9UVjhET1NZcE1yYUNkUGhVUHBWZmpLYmdnOVZTd0lmL0tOamRFZVNKS08vYXhqNVNNM3F5aE9obGUzdXdlMGEKWWlpa21saVU3Wkc5THhJTTFVZ0ZaN24wNURVV2MyN0RWTHYwUSttNVdGZ2VPSi82WklqTTg1RStYcDJ0c0svZwprNDVsbm1iMVNGb1l2LzJuWHEzdkJNaGJPUUVrNDY1WE5OZmRDU1c3RDhCcVJHYitoU1NmT0NHQVlDQkNxVjhKCnBNNHBadFhZZkczT0tCUmRXSTR6VU5JazdiU3lWQW1LWU5Pc3Frby9UMi90NmM0em1ZbU4wTjdMMVljOTk1algKLzBlbE51NW1vMjNOKzJwSzRuaFZjQXZ3VE5HdnhMbWdlcVV4cW44TVdPS29LVGdYNys0PQotLS0tLUVORCBDRVJUSUZJQ0FURS0tLS0tCg==\"\n" +
                "      },\n" +
                "      \"hostname\": \"*.https-valid-test.thecdn.example.com\"\n" +
                "    }")
        private val EXPIRED_CERT_DATA: String? = ("    {\n" +
                "      \"deliveryservice\": \"http-to-https-test\",\n" +
                "      \"certificate\": {\n" +
                "        \"comment\" : \"The following self signed certificate which expired on 3/5/2019 \",\n" +
                "        \"key\": " +
                "\"LS0tLS1CRUdJTiBQUklWQVRFIEtFWS0tLS0tCk1JSUV2UUlCQURBTkJna3Foa2lHOXcwQkFRRUZBQVNDQktjd2dnU2pBZ0VBQW9JQkFRQ2paZ0xpTHNRS0o0UXgKS0F5WnFqT0NOL2lXUmgzdXkyZCtyd0J1VWJ2NTBIVEk0QUdnOHE2eG9pbzNtZHFHZXRXdVJIemUvSmQ5ckxJSQpvWDFXOGNFeTVybW0wV0xXYnJDbzlJQUE5K0dHcTgyNXUreGplemdiSHg3TEt1N3lqdUJVVjI4SyszTXNOQUhGCjZsemZNdTJ5VEFUMExvU25waDRWeHZLRDlzM05rNzdtaW5vcVR0aGFlSldxWXVEQlZ4WTBNS3JGbWJuQkEybkcKV295ZHIrelBROHB2N3Fka0FmcXlnZ0loWjloM0JBNnVESHRtcDNueWlsc2ZSMHpGeFR4QnRhaVhTbnRZdXM4NAphVUNwaHVjWkIvYW5tVHJvMHFSSG82czFhcWd1alJIK2xtWXNySjRjQ01QSmd4eG5YWTM0MUpDZm42T0N3c1FXClNhS3huaS9EQWdNQkFBRUNnZ0VBQytQamQ5UVJYZS9NTmN1RlJ6VlVkRGhnZFliNnJLTE9rREJwNXAwNkFZN0MKd005VUx3TVo1VUU0c3owVjRzMVRlVS93aWtWMVBLYnhlYUZPdnFIdS9pWStBajZnWTV4QWJMc0dDWXdBTkUyUwpOZDdQNzlsS2x1YW4xZjcwemwvSlFUbnZrYXdFa0lYa1R5T2p5SFlyUjlzeVRSYUpmcTJlNk5UR1Z3WUJxZURsCnJvTFByYkp4OXh4TXdPb2ZoN0tHSkZMQ0E4Q1JheGd2U3RJU2JpT1RGTWN5UThOcmhvNlNpOVJJVHNtT3kvTHcKVjF5d2JFaVpoOG5LNFdxd2pSYmR4NzJ5SWxMZ2RUeC90bkFiOUNLM1BqdkZyRTRZajFqbjc0NGF3NGZYUTMyOApiSC9uYWRENmlCeWQ0dmpLUjBLUWlRK0E4ZmViRW5EZzFSVW8rQndpWVFLQmdRRFRDbGQxV20vN3ZqdzFUMkx5Cnhlb3NFdlloYW1DclVRVjRyTCtRcmg0S0JTckVHVEZoZXMrclhBNUxEY29iS01hY1RwSS9LMXVVaHg5K3ZLV1kKVWk0blVKVjAyU283UnEwbW5tWVo2aXhLd0NQQWpESEwrQmxlcVgycFR1SU1hYUpWV2NleFI5dkNneTZVUmJLOQp4OUtWdzVqcE5HYVloK3RSRlV3cE5hR1Nzd0tCZ1FER05XT1d5MXJUdVNlM2hUTG0yakhTVGRlV0RSWlE5eEo3CkFQSmNtaW9xRHIxV1JVR3ZxV3Rkd1kySEl2blNzbDRSa2wxUU03bnpQNHNqN2ZSUjllY2dMcDRXcGFXMTVGKzgKOHlGMXBOcVFSWjZYNkd1Z3JZdUZlT1J5YnZTdGFYLzR6bklQUUpadmJVOVF3OUZnU051cFgwTXJPMCtJWDdXQwpRRWQvR3pJMnNRS0JnR3dVSU1RbDQ3RytOQ0Z0SFpTTlRTYnpNdi9iOWRQbXMzR2dycDZPdlMyT2hkOVZzNWRqCmlOVU9XUGVSQVU4MWE3bUM5NXpJUEtkdEovRUU5WjF6Z05WN2pIOEI5SUhVNlRvYzV0Y2d1VHd5K0Z4VXIrL3cKaURXVmdaaGlvSnVRd2FVS1RKMTYybzNjRnMreWZoNTVKbHl5aGkzd094YWtqUnZDVjNYSFZJN0hBb0dCQUp0SAowbGlkd2U5aS9CR1RnWmhIMG9aT3c1bmpjTnRIWlN3R1J0bHpVWnNYWncvQ1BENnhQTkw3d3JQZkc5Y01OQlFTCkZaYXluM2hKRE9tK0R3MXkxM3BuNnlRVTYraS9ISjM3My9lNWloMUMzWWRtNTRLKzB6Sml6cDR6L080cVc3NkIKaGV3YkRvQUhJLzlER2JJVUFqc0R6YXg5ejhZb0xSdjQzY3BmZFF4UkFvR0Fabm9iSnhJMWVybERsSHBrN0pibApUVnBhUWgvZVBNajgxYXQvNjcxdXRUb3RMdHlvTEZkRE5sb0JlNUVSc2luOXBjaHhSbkNNVVdsRzUxZjNaTmViCkNmbEtVN053UjNZVWRZYU9OVWdEZVp3RkhqOExYMGVPUVQrMENjamR2L2FoWVVIb2JLNWdvdkxXd1ljSHdIY2wKL09vTjhmODVBVDBIaTJ1VldwS2VrZmc9Ci0tLS0tRU5EIFBSSVZBVEUgS0VZLS0tLS0K\",\n" +
                "        \"crt\": " +
                "\"LS0tLS1CRUdJTiBDRVJUSUZJQ0FURS0tLS0tCk1JSURxakNDQXBJQ0NRQ0Q1OFZ3U2IrNWJqQU5CZ2txaGtpRzl3MEJBUXNGQURDQmxqRUxNQWtHQTFVRUJoTUMKVlZNeEN6QUpCZ05WQkFnTUFrTlBNUXd3Q2dZRFZRUUhEQU5FUlU0eER6QU5CZ05WQkFvTUJrRndZV05vWlRFTApNQWtHQTFVRUN3d0NWRU14TURBdUJnTlZCQU1NSnlvdWFIUjBjQzEwYnkxb2RIUndjeTEwWlhOMExuUm9aV05rCmJpNWxlR0Z0Y0d4bExtTnZiVEVjTUJvR0NTcUdTSWIzRFFFSkFSWU5kR05BWVhCaFkyaGxMbU52YlRBZUZ3MHgKT1RBek1EUXlNekl6TkRKYUZ3MHhPVEF6TURVeU16SXpOREphTUlHV01Rc3dDUVlEVlFRR0V3SlZVekVMTUFrRwpBMVVFQ0F3Q1EwOHhEREFLQmdOVkJBY01BMFJGVGpFUE1BMEdBMVVFQ2d3R1FYQmhZMmhsTVFzd0NRWURWUVFMCkRBSlVRekV3TUM0R0ExVUVBd3duS2k1b2RIUndMWFJ2TFdoMGRIQnpMWFJsYzNRdWRHaGxZMlJ1TG1WNFlXMXcKYkdVdVkyOXRNUnd3R2dZSktvWklodmNOQVFrQkZnMTBZMEJoY0dGamFHVXVZMjl0TUlJQklqQU5CZ2txaGtpRwo5dzBCQVFFRkFBT0NBUThBTUlJQkNnS0NBUUVBbzJZQzRpN0VDaWVFTVNnTW1hb3pnamY0bGtZZDdzdG5mcThBCmJsRzcrZEIweU9BQm9QS3VzYUlxTjVuYWhuclZya1I4M3Z5WGZheXlDS0Y5VnZIQk11YTVwdEZpMW02d3FQU0EKQVBmaGhxdk51YnZzWTNzNEd4OGV5eXJ1OG83Z1ZGZHZDdnR6TERRQnhlcGMzekx0c2t3RTlDNkVwNlllRmNieQpnL2JOelpPKzVvcDZLazdZV25pVnFtTGd3VmNXTkRDcXhabTV3UU5weGxxTW5hL3N6MFBLYis2blpBSDZzb0lDCklXZllkd1FPcmd4N1pxZDU4b3BiSDBkTXhjVThRYldvbDBwN1dMclBPR2xBcVlibkdRZjJwNWs2Nk5La1I2T3IKTldxb0xvMFIvcFptTEt5ZUhBakR5WU1jWjEyTitOU1FuNStqZ3NMRUZrbWlzWjR2d3dJREFRQUJNQTBHQ1NxRwpTSWIzRFFFQkN3VUFBNElCQVFCNUhCTFgvWU01QnQvTEVWUmFvazFBL1pSSzAvTmZjVzJLb0VQMk80VklvSEM0CnhqaGFzaERqWWdrME44d1NRTTd2UGxzR1NnUzZzSC9yM3NSeUt0bmZvNzFGMFh1K0lLSXV0Ylh2bmhjdXNXd0QKWXJUMExGaWQzUXl5TUNUTXRBMEpxTVdma3lIOWhlTk16cFI1blg3ODIyUFZzekhEUmpVZUhTSTZwbzB0TUNxZwpyVE10SHVSbVdJaGhhZzY1a29PMUNYTG81R3pkdGdmdTFwb2YzTnRWKzBqQWVidlFtUktqcWZBZUc3WXJTVEpwCk5yalVHdmZJMnpDZElDY1dIbUdTbndXNktSYXFOUFpoVHN2UWhyTEdMZDB0SU02MXZ0NjhPZWNFWXA0eWhlYnQKZFpjQmYxYkdMRWtiWlphTVVuaW9VZW1XSDJoYVVNcDdueWJxV2VQWQotLS0tLUVORCBDRVJUSUZJQ0FURS0tLS0tCg==\"\n" +
                "      },\n" +
                "      \"hostname\": \"*.http-to-https-test.thecdn.example.com\"\n" +
                "    }")
        private val MOD_MISS_CERT_DATA: String? = ("    {\n" +
                "      \"deliveryservice\": \"https-mod-miss\",\n" +
                "      \"certificate\": {\n" +
                "        \"comment\" : \"The following certificate and key are for the same subject but have " +
                "mismatched modulus between the private and public keys\",\n" +
                "        \"key\": " +
                "\"LS0tLS1CRUdJTiBQUklWQVRFIEtFWS0tLS0tCk1JSUV2QUlCQURBTkJna3Foa2lHOXcwQkFRRUZBQVNDQktZd2dnU2lBZ0VBQW9JQkFRQ3lIMW91SmpXcE5SeVAKdE1nVDM3emhCc0tYM1VhTWlhdm5CUHNJUDhidmdaUnNicFNYeVVSdTJsaWdsYWZpWlAybTZxZE1LZ3BHcUo4ZgpwZHQzODFsMHduakI5YURleU92NzZrcnJBdzhlME9VOW9ZU0Q1VVlWMU11M0I4ZTV1UGFLYkdNcDZvc3o1WDJSCmlDYnRjcTAvUzJaeWhWNXIvRkJqNUtsN2I0UlBNTjdPVXJNVW5LcHlXZ2hZNzdXOXpzVm96cTg5cldOL0g5VUEKZjFMRmFSdU1mckNvYVVKZHRKZEIyY2FGblkwWmI1MDhzcmFGWXplaFUvK3FjZE9heWtVa0hMMTVweDVFbS9mOQpYVlpNcmVJeFlobVdCK1I5MWJ5d3dsVmZCSm4zU3dIT3ZGMzVqSTh4d1dpMEx6V0x0Qk9pVm4yYlV3ZUN3dlVQCkdKb1VqbEJ0QWdNQkFBRUNnZ0VBRmpvWFZMN3IzMHVEWHVOZVBDeWxNeWRXelFDTnR5Zk96YXN2Y0I0VlF2blcKZlpsbTdYSHVHaThnOUJqNHRDV0tDWFFxb0RSMng4NXUzTklqaXRwUkJXTG5FcjBGOEFiK2U1Y0c5a0NSZUhUMAp4allMaFRIdEJ2aGcyMXdiTGkvSWhBbDJibHFZT0VlZzNiSXh1VnVnQnMvdjNzYUp1OHZtZERDcWZYNnk4ZmFmCnNFYmVTT2dkRFltRTg1SHZXUFFDTVVNV0MyZzJEekw2a0U1eCtxMmt5VkNlc1hSVTl6c3BtRkxsMGw5cndIN0sKbU9jMUVJZ3J0THExeUt6b3BKTUFTbVZPVmxTd1FUdGlOS3RGN3FZVXVudmpDdC8wWDM1clFIcDMwWXBCbVpHUApUQ0swMEFZMUZteDhUK2p5MENvdmVOWm9lM2JVTk5RM2l0MEhObWNWdVFLQmdRRHFiNVRFQmQ3aGw2cVRaS09xCkx3T1M5M2xJcU5nbFZnbzN2S09HUkF1MWpFYm9IaktDOTdudUdGSGNGdXZzTldISEpQaHRQUTJEcXUzREM1NHkKSXBOZ2Q4dVUwZWVBNjdxM2g2cWpJRjQ2c1pMZWdpaTh0aWlOZTJUK0dQVjBHYngyNU1XYjJtTVNXSjJUQU1UWAp3eHNEb1d5QkZEajU2MEJacXBqbzFTQTdkd0tCZ1FEQ2diaklUNWI0TWpFcHlyTEJKeUwvc1BjQzFUOWczUHdECjlvSGk0aXJFSWJzeDk3NTNhZnRpM3VOUlFZUHI1WW9qdkNQSHNibnQ2SXpaYUZaQUViem9Zc1ExSUJ3UkhDbVUKbWhiS0p2MDRhckVScTZtV1hIZng4MkszUC92a1VraFZSTzZHNzJ4eFB3UFJ6b3dteDZDbU9uUWVadkYrZTZNRwptc0xlQjVORU93S0JnQVp2YjY3OTFrTnREV0trWlpXN1dxYkRJbElyU0Z1bUEvdkpzdGR4c0x5WUVDNDQvZnY0Clh1TTVTYTMzOXh2eHp6QlBSSDZES1liT3YxNFdTSTVwd28vb1dlOUkzOGo3TDVId0tHLzM2SDVGOTVraUM0bzYKbWR4Z1ljSlQzeEVEejllWHFoRUFLcTRMUHJBVldsSHQ2aVRzWG5VZ24vdkVTR3p0c09yYlJ0bzdBb0dBR1VsVQpCSGFVWWQva2xGSk51dDZqcGlvVGNzTFdZbmxZS2d1NkJ3endFbDl3UHFhK2xEZXEvc2VMTmQwV2tXeGQ4UmRjCmIzR2pnbEpoUFVKYk5Da2FMZnZwRmg3K2h4cnFMTzk3VnZ5S252TC80aFEzRDkwbG1zYlJacEZpNWVQc2sybEsKdVRBWElRSFlOVVpzNGYzQjNOcHNqaWREN2ZXVTFCNzZobkxscWxFQ2dZQU5HOWZ2WFNhOWlnUC9acmdBQW55KwpLdlZUb1NOK0I3UDhGSUk4QW4zNTdzYXY3K0ZVWnZwZkQ1S1hkSHBZUEIxTk9tQVhGcE9FYjVvQTErZUNFRTh4Ck4wdktEaHoyK3VBSEFyczBud1ppUHBXbkZoaE5sLzJ5b1pUZHB0VWp1K0ZhSVg4RHFtSUovNGVMdVNERUR4MmUKcUVDTGt0SUxJWkk3eXVaNFIwNGFxZz09Ci0tLS0tRU5EIFBSSVZBVEUgS0VZLS0tLS0K\",\n" +
                "        \"crt\": " +
                "\"LS0tLS1CRUdJTiBDRVJUSUZJQ0FURS0tLS0tCk1JSURyRENDQXBRQ0NRQ0pZQWd0a1JtN3hUQU5CZ2txaGtpRzl3MEJBUXNGQURDQmxqRUxNQWtHQTFVRUJoTUMKVlZNeEN6QUpCZ05WQkFnTUFrTlBNUXd3Q2dZRFZRUUhEQU5FUlU0eER6QU5CZ05WQkFvTUJrRndZV05vWlRFTApNQWtHQTFVRUN3d0NWRU14S3pBcEJnTlZCQU1NSWlvdWFIUjBjQzF0YjJRdGJXbHpjeTUwYUdWalpHNHVaWGhoCmJYQnNaUzVqYjIweElUQWZCZ2txaGtpRzl3MEJDUUVXRW5SalFITmxiR1l0YzJsbmJtVmtMbU52YlRBZ0Z3MHgKT1RBek1EVXhPREE0TkRsYUdBOHlNVEU1TURJd09URTRNRGcwT1Zvd2daWXhDekFKQmdOVkJBWVRBbFZUTVFzdwpDUVlEVlFRSURBSkRUekVNTUFvR0ExVUVCd3dEUkVWT01ROHdEUVlEVlFRS0RBWkJjR0ZqYUdVeEN6QUpCZ05WCkJBc01BbFJETVNzd0tRWURWUVFERENJcUxtaDBkSEF0Ylc5a0xXMXBjM011ZEdobFkyUnVMbVY0WVcxd2JHVXUKWTI5dE1TRXdId1lKS29aSWh2Y05BUWtCRmhKMFkwQnpaV3htTFhOcFoyNWxaQzVqYjIwd2dnRWlNQTBHQ1NxRwpTSWIzRFFFQkFRVUFBNElCRHdBd2dnRUtBb0lCQVFEZ0Noa3J2SFMwK21uWUtUR1Y0NTl2N2ZURXA1ekVsMW4xClViQlI2aFNVbXZ6eUJYYWVpdU5wQ2NubUhSd01ma3c4dTNuNVVvMFhDOGY3Nllkc3gyRzlkL1h2dVdlMUFNdDQKWGF0SjVLa1AvNVNpVS9reWVhR3VwbmFTWWtvRHBKRlEzbTJ0cXhJeGh6ZklteUIzTFdJdEFUU3kzMGxtd0E5Ywp1cTRZaWNrSjVXUWxNYklNVW00OHlCcU5NOXlma1lRSy90WWwyb3l6Q1ZrN2s5MTdQdDFKS3JpcThrRTFKdDhlCkNqbUtxUXZMTjRLdzJ5dm9aNTZvTWtuaDlhRVAzRm55RDIzRWNtdGlXbWI0Q0hrSXowL20vczVGV0tLUDhzTHkKOHhCeUlUWjZPVUNzNEZUeVNiVGk3ODlGaVJ1WjljcE9rZVdDMFdQMkV0L1g5MmlZRnZIckFnTUJBQUV3RFFZSgpLb1pJaHZjTkFRRUxCUUFEZ2dFQkFITGJRczNrMnhpdVBpMjdWNVJnVFFHdytZb2YyNXQvcjBMVVVPOTE0TlZBClh1MjAyUDVMNy9wSTVFZTVLTi9ZVHQ2SnVoWWl2M0ZiQnZBd3FTaE43N2pHTGNFQi94VDhCVWxsbzBISzJteTYKR2YyL3BHZWxNL2syTFpxZEpMTXhCL2JsL3JGZUJCVHhWVkdJd3M4ZFZ3d1BuY0x0bnBEVmQzRnlOMGx5UzdMNQovVXQ2Wm00QTBlVEpxZUVlcnprRDZMQWVEY21vTXg1cGphZDNodldGTCsrdjdkazQ5T3QrSkNtSGx5ZTR6eEE1Cmc5RUQ0VXpaYnFZcmZ4Uk9lY25zTWJzUmllVlcyekh5dDQyVTVUSmtKK3JNdE9DempROUwrTGRDVTZhOENVVjUKWlBnYm1vMTJkN1NIUDBaY1FtOTg5THpEcXRRblMyRnJrS3pSdEh3ZHBjND0KLS0tLS1FTkQgQ0VSVElGSUNBVEUtLS0tLQo=\"\n" +
                "      },\n" +
                "      \"hostname\": \"*.http-mod-miss.thecdn.example.com\"\n" +
                "    }")
        private val VALID_SAN_CERT_DATA: String? = ("    {\n" +
                "      \"deliveryservice\": \"https-san-test\",\n" +
                "      \"certificate\": {\n" +
                "        \"comment\" : \"The following is a self-signed cert with a subject alternate name (but not CN) matching the hostname regex\",\n" +
                "        \"key\": " +
                "\"LS0tLS1CRUdJTiBQUklWQVRFIEtFWS0tLS0tCk1JSUV2Z0lCQURBTkJna3Foa2lHOXcwQkFRRUZBQVNDQktnd2dnU2tBZ0VBQW9JQkFRRFlVUDZEUmJKYk91RG4KTEtlZC9vd1ZXWVBmRFNnbWRESTcrdTdpS2N0WXlrQ2xUY3FjUjFPcnhBMVF4UjlMbGdCbDZFTmxNcGt5akllSwpSZ2Q2dm9nMlZKUDdhcUNwaWJyeDl0VWk2Z1IxUmx3TGtMejRqT1EyTFVNcFd6dFlIMXFMYnlFaXRrNlBOYzNnCnllT1BVa1NQMjE2VE5YcC9PcERkR2ZYTzYzczJYQVhGcEVvSGtVekFEUDJKNllUcjI1RkVsRVNkNjJnOXQrQWsKenkzajBCbmtiVjIzSTRJc1VrdU5CQk95VmpBQ3lncENYZmI2a1hBa1JTbk1SOERPUXE3czJHdElGQ0FOU0pObwp2SEd5RmU2VWVLNGwvV1BodHRhc3ZzOEVPc0NKSjExQzVxenhDRGNRN1liUm5IUHo0a2FLemRxMm9KTWg3MjZUCkNMc25odmtmQWdNQkFBRUNnZ0VCQUsvanc5K254MnZwQjBEU0RZVXBtbXd0dWsyZkhCdVFkZHhSb1Baak40WisKQm15alFYRXJpanhya013eFRNNGdMVGl2MEVVMERGK081eE1tK0NQMVV6cWlNU1hJd05TNk9qbFBGR1ZzVmEycQpSV1BlRDlvbWJkWWpuYTIrRWhZSWdJNUtFVzV4UUpXd0VUU0wxSzRRSGRHL2RUcEx6TXd4S2VPdW5USGdSZktmCkNCeW00Wk4rOXRkU1F1anVoeXZRUk5hR21MVjRqaGREbnNHMXpteWRQWjhBZzNtZnNrRldVN2lHckJ6cjZ6bGIKZy9FZUxURUpLVms5NWZVYkxLNnVPTUdqUzVXSFhDWm1Bc2lIcHl3T29jcTdnbmRtRzhTTVRXR2oyc1VmZDhkKwpFTkhMdDVYeml6SENudC9zcEVFbmV4Q3lBQXV5NThOUFd5Tm1KTzk0VDRrQ2dZRUE2MnpvcEdjL05hUkp6SkI1CjZhbkJTSndQVzNldXdET0xWMWpEU1NBdnowZTV4VHRZUXk2ZWNnNFU0V3djeG5MenFoTDMrYlVDMmsxNERtZHEKa1E5eGJFQUxPUXBrdGoyZjhBMytBZ09NVk1zWjhkMlNBbWtpZTFWN3p3Szl4T3QzNGEvckx3RjVsSlNPQnlxcApGT3Y3NWRQTmRxdVZ4dUZiM0JkOEloajVjVk1DZ1lFQTZ6aVF3L081bWF6QVZQSWJsc1VIVWIrWWNWRjNnS3dBCnJ2UWVZUFI1aVZpbS8wMkM3V1VoTlJsZHoyQmZTOExvNzltdldyeTM1SnV6aDVNNkNnSzkxZmRUWGF5NW1ETWUKcmxMK01qL3RMdlZkRTcvb0N5ZU9Gb0p3V2tEMUdqWjQ4UVNlUnptRTBjZVpnMjloWWIzRlVNN1g2eHZkc2h6WgpQTkJ0K3Z6MFk0VUNnWUIzbkhqQ2RwWjkyWCtKU0dvNFZvQVdOUHYwZStVMFQ3dmU0QUV3R1FOUmE5NnRuNDRqCmEvOGljWmNZUk1hRjZRZDFoSGVxemRXcU5pdm1IWkxlS3B0MDVVRU9kUUpnT1FVV2dQVnA3b1dRMnpQT1Y4V0UKSmdoWDQwR3BGbnAySHpCa1lPMjdqeU5IWXdhYUM0bW1VSk5GM0l1S1hIa1hkNDRsVDJWSktha3Fjd0tCZ1FDUApMSkFaWmZuY0MyWEtqcyt5TnQyY3FjV05mQ3dFT3kzelVBNUpyNlZtcjNwK3ZkeHF5WHFzQ25PcWJadXp6c0RnCmRaMGgydXNJRUtuM1pPc1grdEU0TXVJZ2k2a3dkalUvb0s2dlFYQUtmRGthYzE0M28rejkwSW00dGY4NjVGczcKUVRkaVhTVjJWMHNlMEtYeXk0TVdDOEVyajN1akZhSTVQUmtrZytIeHpRS0JnR2daTlRXT3JiRGNhSzZyMGJqRgpjL094ZUZaMzBaR3dWekdxUm9QeTAzZmcyUGIyanZ2c1dwcmExeEhoOVl3ZTA1Y0xlWk5sU2VIR1kwc3V4N0NSCi8rUVZPMDRnQnFZVWJ3RVd0OW1ZUHFUUGlrZ0l1TXZ3dC9VOFB2NGUyM3N5dmNBckM4L01iaGloMUc4OUdJUkgKWG83ZENZNENIbEZhd1kvN21lRFFSS2NsCi0tLS0tRU5EIFBSSVZBVEUgS0VZLS0tLS0=\",\n" +
                "        \"crt\": " +
                "\"LS0tLS1CRUdJTiBDRVJUSUZJQ0FURS0tLS0tCk1JSUQ2VENDQXRHZ0F3SUJBZ0lKQUsySVZqdWJDWTFyTUEwR0NTcUdTSWIzRFFFQkN3VUFNSUdQTVFzd0NRWUQKVlFRR0V3SlZVekVMTUFrR0ExVUVDQXdDUTA4eER6QU5CZ05WQkFjTUJrUmxiblpsY2pFU01CQUdBMVVFQ2d3SgpTMkZpYkdWMGIzZHVNUlF3RWdZRFZRUUxEQXRNYjJOdmJXOTBhWFpsY3pFWE1CVUdBMVVFQXd3T1kyOXRiVzl1CmJtRnRaUzVqYjIweEh6QWRCZ2txaGtpRzl3MEJDUUVXRUhSbGMzUkFaWGhoYlhCc1pTNWpiMjB3SUJjTk1qQXgKTURFeU1UVTFNek14V2hnUE1qRXlNREE1TVRneE5UVXpNekZhTUlHUE1Rc3dDUVlEVlFRR0V3SlZVekVMTUFrRwpBMVVFQ0F3Q1EwOHhEekFOQmdOVkJBY01Ca1JsYm5abGNqRVNNQkFHQTFVRUNnd0pTMkZpYkdWMGIzZHVNUlF3CkVnWURWUVFMREF0TWIyTnZiVzkwYVhabGN6RVhNQlVHQTFVRUF3d09ZMjl0Ylc5dWJtRnRaUzVqYjIweEh6QWQKQmdrcWhraUc5dzBCQ1FFV0VIUmxjM1JBWlhoaGJYQnNaUzVqYjIwd2dnRWlNQTBHQ1NxR1NJYjNEUUVCQVFVQQpBNElCRHdBd2dnRUtBb0lCQVFEWVVQNkRSYkpiT3VEbkxLZWQvb3dWV1lQZkRTZ21kREk3K3U3aUtjdFl5a0NsClRjcWNSMU9yeEExUXhSOUxsZ0JsNkVObE1wa3lqSWVLUmdkNnZvZzJWSlA3YXFDcGlicng5dFVpNmdSMVJsd0wKa0x6NGpPUTJMVU1wV3p0WUgxcUxieUVpdGs2UE5jM2d5ZU9QVWtTUDIxNlROWHAvT3BEZEdmWE82M3MyWEFYRgpwRW9Ia1V6QURQMko2WVRyMjVGRWxFU2Q2Mmc5dCtBa3p5M2owQm5rYlYyM0k0SXNVa3VOQkJPeVZqQUN5Z3BDClhmYjZrWEFrUlNuTVI4RE9RcTdzMkd0SUZDQU5TSk5vdkhHeUZlNlVlSzRsL1dQaHR0YXN2czhFT3NDSkoxMUMKNXF6eENEY1E3WWJSbkhQejRrYUt6ZHEyb0pNaDcyNlRDTHNuaHZrZkFnTUJBQUdqUkRCQ01FQUdBMVVkRVFRNQpNRGVDREdacGNuTjBjMkZ1TG1OdmJZSW5ZMlJ1TG1oMGRIQnpMWFpoYkdsa0xYUmxjM1F1ZEdobFkyUnVMbVY0CllXMXdiR1V1WTI5dE1BMEdDU3FHU0liM0RRRUJDd1VBQTRJQkFRQ3VJT3JWN3FYNWltT21hMHNPWjI5d1k1TWwKdm1WaWxuM0pEUjI0T3o4M3VDMVZkeE1VWlJNa1RVY3ZYcG9pN2Z6Zm5CWU1oYzJUb1lrZi9BSkhrVk42bFg5OQowNEZETENMSDlWbWtRczB0blpVRktWSlpGMXp2Mlo5RXVjNCtNTnZVSTlYQ3JnbndxZDVIWm44Z0FrRHlsNW1pCkFRUXZiVFhrdmFCNlpZb205d2dnRU4yZ0p6cXk3LzVEU0VIcTE3R01aVCtEekQrNCtPUnRidm90OXIyaVFWczIKTDRPd3g1VjBKYzhwKzhpSUtvWXRhVXp5K0RBMXg0WkFkSVQ4VlVoY1N4WHROUmloTU5sTHRYMHZvRUozTU81VwpmQWxCaVhwOUVoek9meHRpc05idTdzWVQxdFdhaHRUMHp5VlIvOUs5K0U5Q0crUFhFT3dUcmxjZGIxdDIKLS0tLS1FTkQgQ0VSVElGSUNBVEUtLS0tLQ==\"\n" +
                "      },\n" +
                "      \"hostname\": \"*.https-valid-test.thecdn.example.com\"\n" +
                "    }")
        private val VALID_SAN_WILDCARD_CERT_DATA: String? = ("    {\n" +
                "      \"deliveryservice\": \"https-san-wildcard-test\",\n" +
                "      \"certificate\": {\n" +
                "        \"comment\" : \"The following is a self-signed cert with a wildcard SAN (but not CN) matching the hostname regex\",\n" +
                "        \"key\": " +
                "\"LS0tLS1CRUdJTiBQUklWQVRFIEtFWS0tLS0tCk1JSUV2Z0lCQURBTkJna3Foa2lHOXcwQkFRRUZBQVNDQktnd2dnU2tBZ0VBQW9JQkFRRFlVUDZEUmJKYk91RG4KTEtlZC9vd1ZXWVBmRFNnbWRESTcrdTdpS2N0WXlrQ2xUY3FjUjFPcnhBMVF4UjlMbGdCbDZFTmxNcGt5akllSwpSZ2Q2dm9nMlZKUDdhcUNwaWJyeDl0VWk2Z1IxUmx3TGtMejRqT1EyTFVNcFd6dFlIMXFMYnlFaXRrNlBOYzNnCnllT1BVa1NQMjE2VE5YcC9PcERkR2ZYTzYzczJYQVhGcEVvSGtVekFEUDJKNllUcjI1RkVsRVNkNjJnOXQrQWsKenkzajBCbmtiVjIzSTRJc1VrdU5CQk95VmpBQ3lncENYZmI2a1hBa1JTbk1SOERPUXE3czJHdElGQ0FOU0pObwp2SEd5RmU2VWVLNGwvV1BodHRhc3ZzOEVPc0NKSjExQzVxenhDRGNRN1liUm5IUHo0a2FLemRxMm9KTWg3MjZUCkNMc25odmtmQWdNQkFBRUNnZ0VCQUsvanc5K254MnZwQjBEU0RZVXBtbXd0dWsyZkhCdVFkZHhSb1Baak40WisKQm15alFYRXJpanhya013eFRNNGdMVGl2MEVVMERGK081eE1tK0NQMVV6cWlNU1hJd05TNk9qbFBGR1ZzVmEycQpSV1BlRDlvbWJkWWpuYTIrRWhZSWdJNUtFVzV4UUpXd0VUU0wxSzRRSGRHL2RUcEx6TXd4S2VPdW5USGdSZktmCkNCeW00Wk4rOXRkU1F1anVoeXZRUk5hR21MVjRqaGREbnNHMXpteWRQWjhBZzNtZnNrRldVN2lHckJ6cjZ6bGIKZy9FZUxURUpLVms5NWZVYkxLNnVPTUdqUzVXSFhDWm1Bc2lIcHl3T29jcTdnbmRtRzhTTVRXR2oyc1VmZDhkKwpFTkhMdDVYeml6SENudC9zcEVFbmV4Q3lBQXV5NThOUFd5Tm1KTzk0VDRrQ2dZRUE2MnpvcEdjL05hUkp6SkI1CjZhbkJTSndQVzNldXdET0xWMWpEU1NBdnowZTV4VHRZUXk2ZWNnNFU0V3djeG5MenFoTDMrYlVDMmsxNERtZHEKa1E5eGJFQUxPUXBrdGoyZjhBMytBZ09NVk1zWjhkMlNBbWtpZTFWN3p3Szl4T3QzNGEvckx3RjVsSlNPQnlxcApGT3Y3NWRQTmRxdVZ4dUZiM0JkOEloajVjVk1DZ1lFQTZ6aVF3L081bWF6QVZQSWJsc1VIVWIrWWNWRjNnS3dBCnJ2UWVZUFI1aVZpbS8wMkM3V1VoTlJsZHoyQmZTOExvNzltdldyeTM1SnV6aDVNNkNnSzkxZmRUWGF5NW1ETWUKcmxMK01qL3RMdlZkRTcvb0N5ZU9Gb0p3V2tEMUdqWjQ4UVNlUnptRTBjZVpnMjloWWIzRlVNN1g2eHZkc2h6WgpQTkJ0K3Z6MFk0VUNnWUIzbkhqQ2RwWjkyWCtKU0dvNFZvQVdOUHYwZStVMFQ3dmU0QUV3R1FOUmE5NnRuNDRqCmEvOGljWmNZUk1hRjZRZDFoSGVxemRXcU5pdm1IWkxlS3B0MDVVRU9kUUpnT1FVV2dQVnA3b1dRMnpQT1Y4V0UKSmdoWDQwR3BGbnAySHpCa1lPMjdqeU5IWXdhYUM0bW1VSk5GM0l1S1hIa1hkNDRsVDJWSktha3Fjd0tCZ1FDUApMSkFaWmZuY0MyWEtqcyt5TnQyY3FjV05mQ3dFT3kzelVBNUpyNlZtcjNwK3ZkeHF5WHFzQ25PcWJadXp6c0RnCmRaMGgydXNJRUtuM1pPc1grdEU0TXVJZ2k2a3dkalUvb0s2dlFYQUtmRGthYzE0M28rejkwSW00dGY4NjVGczcKUVRkaVhTVjJWMHNlMEtYeXk0TVdDOEVyajN1akZhSTVQUmtrZytIeHpRS0JnR2daTlRXT3JiRGNhSzZyMGJqRgpjL094ZUZaMzBaR3dWekdxUm9QeTAzZmcyUGIyanZ2c1dwcmExeEhoOVl3ZTA1Y0xlWk5sU2VIR1kwc3V4N0NSCi8rUVZPMDRnQnFZVWJ3RVd0OW1ZUHFUUGlrZ0l1TXZ3dC9VOFB2NGUyM3N5dmNBckM4L01iaGloMUc4OUdJUkgKWG83ZENZNENIbEZhd1kvN21lRFFSS2NsCi0tLS0tRU5EIFBSSVZBVEUgS0VZLS0tLS0=\",\n" +
                "        \"crt\": " +
                "\"LS0tLS1CRUdJTiBDRVJUSUZJQ0FURS0tLS0tCk1JSUQ1ekNDQXMrZ0F3SUJBZ0lKQU1xUjhzUmNlL2xOTUEwR0NTcUdTSWIzRFFFQkN3VUFNSUdQTVFzd0NRWUQKVlFRR0V3SlZVekVMTUFrR0ExVUVDQXdDUTA4eER6QU5CZ05WQkFjTUJrUmxiblpsY2pFU01CQUdBMVVFQ2d3SgpTMkZpYkdWMGIzZHVNUlF3RWdZRFZRUUxEQXRNYjJOdmJXOTBhWFpsY3pFWE1CVUdBMVVFQXd3T1kyOXRiVzl1CmJtRnRaUzVqYjIweEh6QWRCZ2txaGtpRzl3MEJDUUVXRUhSbGMzUkFaWGhoYlhCc1pTNWpiMjB3SUJjTk1qQXgKTURFeU1UVTFOVEV6V2hnUE1qRXlNREE1TVRneE5UVTFNVE5hTUlHUE1Rc3dDUVlEVlFRR0V3SlZVekVMTUFrRwpBMVVFQ0F3Q1EwOHhEekFOQmdOVkJBY01Ca1JsYm5abGNqRVNNQkFHQTFVRUNnd0pTMkZpYkdWMGIzZHVNUlF3CkVnWURWUVFMREF0TWIyTnZiVzkwYVhabGN6RVhNQlVHQTFVRUF3d09ZMjl0Ylc5dWJtRnRaUzVqYjIweEh6QWQKQmdrcWhraUc5dzBCQ1FFV0VIUmxjM1JBWlhoaGJYQnNaUzVqYjIwd2dnRWlNQTBHQ1NxR1NJYjNEUUVCQVFVQQpBNElCRHdBd2dnRUtBb0lCQVFEWVVQNkRSYkpiT3VEbkxLZWQvb3dWV1lQZkRTZ21kREk3K3U3aUtjdFl5a0NsClRjcWNSMU9yeEExUXhSOUxsZ0JsNkVObE1wa3lqSWVLUmdkNnZvZzJWSlA3YXFDcGlicng5dFVpNmdSMVJsd0wKa0x6NGpPUTJMVU1wV3p0WUgxcUxieUVpdGs2UE5jM2d5ZU9QVWtTUDIxNlROWHAvT3BEZEdmWE82M3MyWEFYRgpwRW9Ia1V6QURQMko2WVRyMjVGRWxFU2Q2Mmc5dCtBa3p5M2owQm5rYlYyM0k0SXNVa3VOQkJPeVZqQUN5Z3BDClhmYjZrWEFrUlNuTVI4RE9RcTdzMkd0SUZDQU5TSk5vdkhHeUZlNlVlSzRsL1dQaHR0YXN2czhFT3NDSkoxMUMKNXF6eENEY1E3WWJSbkhQejRrYUt6ZHEyb0pNaDcyNlRDTHNuaHZrZkFnTUJBQUdqUWpCQU1ENEdBMVVkRVFRMwpNRFdDREdacGNuTjBjMkZ1TG1OdmJZSWxLaTVvZEhSd2N5MTJZV3hwWkMxMFpYTjBMblJvWldOa2JpNWxlR0Z0CmNHeGxMbU52YlRBTkJna3Foa2lHOXcwQkFRc0ZBQU9DQVFFQUVaaVRYN28zR3RBMm5nK2JTanhPK3k3Z3FZencKNU94dGQ2czBWT1VUQmhLcno5NUFPTFJDR1pjOWxEL3pMSHhVazBzQkRocitpZXVKYTIrNC9mNjE2eFV5TUdnOApBMFlPRzAyMXYzczdkdlRUWExkMWdkamNLeUhhdlZpTTJ0eU00UWJucGtmWVJNT3BROU9OL0dmaU40OGcxemVKCm1sdjA3YXJtTG5PeFoyb01SREZaZjBFVXBxeVIzZk9odVI2M1NqelJCRGRsTDBKUXFIcEpmWWV6TVVPbjgyYjAKeWlSSi9FTGF1SnNHMkRONUNFcHFqbDdTRVd5NUFMYmduZUZSYnlFYVgrckxqMnhNNEZXSmpqd2gwM2lyMTF1VgpSUzU1dFRQL2RwbUkyZmE5c0d6TzZZMmJmeGkyYkVsc2tkVnkxcWEwTGlwSzJoTzNYaGxKMVRxMVZBPT0KLS0tLS1FTkQgQ0VSVElGSUNBVEUtLS0tLQ==\"\n" +
                "      },\n" +
                "      \"hostname\": \"vanity-name.https-valid-test.thecdn.example.com\"\n" +
                "    }")
    }
}