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
package com.comcast.cdn.traffic_control.traffic_router.shared

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
import org.xbill.DNS.Record
import org.xbill.DNS.Section
import java.lang.Exception
import java.lang.reflect.Field
import java.util.*
import java.util.function.Function
import java.util.function.Predicate

object SigningData {
    // If you want to update this data, change the contents of SigningTestDataGenerator.java,
    // run its only test and then replace everything between here and the declaration of signedList
    // All data below is based on PKCS#1 format, see https://tools.ietf.org/html/rfc3447#appendix-A.1.1
    var ksk1Public: String? =
        ("MIIBCgKCAQEAly/1SbKJpzYwOJF2Xie7W6eLyQ/W1Ar8hKss7ZbIkcg23bt8QQOFVLPlYG9luYzAULZgTWa4gFlrBkEzO410oy8V" +
                "FZgB5x11/LioWGJmy9h+H6R1Fy0QFP3eFGKb9tLuAJGMaSRTcbRADJQYiDJ6uuWobTg2fNxlb7B1lz7wOVk/yTV795k+vb+lJx8x" +
                "Zu9vNyIkUy2/LF4J0oXKCPUEee0hpBglEeFcnMSHjO+LtY5Y6E8+fp3d38+Tikmy/2Xu0R35MmCWXuwqYMO+1p7spNzsuUFkhTWt" +
                "0yJKc8pC91V6e3gsD6iwMy3Q0EEEQ7q1z+M9vLIYtmC27mHmdDh1DQIDAQAB")
    var ksk1Private: String? =
        ("UHJpdmF0ZS1rZXktZm9ybWF0OiB2MS4yCkFsZ29yaXRobTogNSAoUlNBU0hBMSkKTW9kdWx1czog\nQUpjdjlVbXlpYWMyTURpUm" +
                "RsNG51MXVuaThrUDF0UUsvSVNyTE8yV3lKSElOdDI3ZkVFRGhWU3o1\nV0J2WmJtTXdGQzJZRTFtdUlCWmF3WkJNenVOZEtNdkZS" +
                "V1lBZWNkZGZ5NHFGaGlac3ZZZmgra2RS\nY3RFQlQ5M2hSaW0vYlM3Z0NSakdra1UzRzBRQXlVR0lneWVycmxxRzA0Tm56Y1pXK3" +
                "dkWmMrOERs\nWlA4azFlL2VaUHIyL3BTY2ZNV2J2YnpjaUpGTXR2eXhlQ2RLRnlnajFCSG50SWFRWUpSSGhYSnpF\naDR6dmk3V0" +
                "9XT2hQUG42ZDNkL1BrNHBKc3Y5bDd0RWQrVEpnbGw3c0ttRER2dGFlN0tUYzdMbEJa\nSVUxcmRNaVNuUEtRdmRWZW50NExBK29z" +
                "RE10ME5CQkJFTzZ0Yy9qUGJ5eUdMWmd0dTVoNW5RNGRR\nMD0KUHVibGljRXhwb25lbnQ6IEFRQUIKUHJpdmF0ZUV4cG9uZW50Oi" +
                "BLa0tXanQ0Z0NpUmtnRHR3\nMmNyRFhWQk5DNHVvNGlhY0JUMlAxbTJ5Yk1XSlNKdVNsTjIyRkVJZzJMN3FzVjM5bDlJU3d5LzJY" +
                "\nTEloNzJLa1BuNUEzeWhXL1cwN0F1NUNQNzBpR1dxUUQyVFpoR0RFOWhCN2tWS1JGQ09vTXVzczZl\nM0ZVTko5bktma3kxOU9L" +
                "Q3ZzRUhnSGgxZ1NLdjYwUCs4VVA0MnhPbVlmK2pSOVFCRC9ibWRBVys4\nbTE5blFJSHBsY2FrYUViczdDU0VpTjlESUFCRTZvb2" +
                "lKOWZqSThyWWl6ZGJiOXVZcnl3dXpaU2xV\nMGd6VHJNSjJReXpkZEdFNzJnZ1ZYWHBRd3FwdGtJOUNqQ1JhWWtZaFU3WUNzWlBD" +
                "aDZycGwrNWth\nNks3ZmxtQ3lXcTZHaWpJc1BVU0xUb2Z4RWNFUXlkL0N3QWdUZWVYSlE9PQpQcmltZTE6IEFPUE5G\nSmh2SERm" +
                "VkhsN0xxTVMrTmd2alk2S2hYYTZ1dHV4OTdnZVQxQ3ZsL1pLTFUyakxLTjc1TTJDM0JL\nOTVMSlB4dTF4Ylg3UDRTWmR6TGpDVE" +
                "xMOW9hV2RaRWNVc3NTM1lSTkdEZEpwbmVPN1lHWWxzaUlM\nRXozZncvTXkvU1pGUlJSNTZtbU0rSmMvVENhUlo5WDYvQXNDVzFr" +
                "dFQ2U2gvdkFUbEt1WGYKUHJp\nbWUyOiBBS25uQlRaNmQrdFlZNVNaSnpTdFRIVGptdDloRm9xNmlTZ0hxUlZNcUhHWitEYWdpdn" +
                "hV\nR1E1ZDFTL0htU1BKKy9nUXJOMGIxeExYVGNockNXbC9TR3d4NXNidlBDQ3MrVFJ5RjRvKzBpQlV2\nbFIxQnY1RnlCZHRiWl" +
                "VFVTUwckVIbSt5ZTlCblJyUlExL2d4QVJKRzdBVTJ2ODNyZC9VSTdUTXB3\nbG1YRXFUCkV4cG9uZW50MTogRUZ4elpUdGR1SGpi" +
                "RkkzbUVqTmpLRmx0bDRtbGM5MWlqL3UvYzlY\nZlFFUGxnWGYyUnFtSXh2TDVDTEk4YU9uNEZLd2crOUlvUG9IUHIwdjdma3VqeE" +
                "IrWXFtNlFReXdo\na1p4ZDNQcG5Eb3Z0K0tHV3UvTUtudkhKd09DVEY1V0prQ0ZaS0lZL3ZtOEJRanJ6RDFYU3R3YTdD\nT0FNaG" +
                "g2QnVZSHJIQ0xsZ05zPQpFeHBvbmVudDI6IEUxWlMxbzk3eEpKcnl3YmZXblI2NWpiZGNw\nMFhOclRpVldjQnpJWE1DaG52RVRD" +
                "V2dQK0ZWb2hCY2syeVZVdHczUnZ3d0wyaGJlaUpWczB2NWd6\neGpqYmp3REtNVWwrOWdVV1cva29HR2wzcXg2d0dIcnlNZUhZZz" +
                "ZidnpWRWtKZUFISjgrd3cyRk9H\nNm91blk2RjZnRUR2WityTFpGVTl0eDh2eDdjWnUvYz0KQ29lZmZpY2llbnQ6IEFMd1hKOGZk" +
                "QWtY\nLzZMeVFvdENIdHZDVWszbEtWbEdlZXAwQlhsYWZyL3FTU2RrTEt2ZHVRSVJCczY0bDBnZW9ndW1H\nRVd5dFViaG9Vak44" +
                "NFNwOG5ldktyS3hqaTlxeG9xUytyQjJ2aElvc0pVdnVoSEJENWMyeHZJVG1R\naXYrazUrT1FDaGdqeGFONnJ5b1NoRDhlYnlsUk" +
                "RzQ1VNL1FFQk82QWZyS0NQZHkK")
    var ksk2Public: String? =
        ("MIIBCgKCAQEAqZz9euL/dovYWIF87eUDWObp9YodiCp88k/PhDCyIbpn7a/QZZYZgYJTjQH0wrMweHj6M74fn8zAFz4fCUq6XSdF" +
                "UR0X/7MXzBYfuKYdYbYrF68C57ryEl3sW9rSMgxWtajvKFzyB67sYT2GcL3vjZPaYb2ZT2jgjEqeKAk7fzcibfR4UrTj3LI19U8W" +
                "reZ29JZAmV2sknYA3ImM33CLYseRCw79r0Jws9RLmJixzDeavWYBls1KUqeXYqV++Hb+mQcR/C0/ch63msPWwgcOEfGFmagcYFvC" +
                "6gOLYXH5WgC2UJlpAMQBZC+3UKwmHaomXeHh92zkQ/nV4bcEOzjZlQIDAQAB")
    var ksk2Private: String? =
        ("UHJpdmF0ZS1rZXktZm9ybWF0OiB2MS4yCkFsZ29yaXRobTogNSAoUlNBU0hBMSkKTW9kdWx1czog\nQUttYy9YcmkvM2FMMkZpQm" +
                "ZPM2xBMWptNmZXS0hZZ3FmUEpQejRRd3NpRzZaKzJ2MEdXV0dZR0NV\nNDBCOU1Lek1IaDQrak8rSDUvTXdCYytId2xLdWwwblJW" +
                "RWRGLyt6Rjh3V0g3aW1IV0cyS3hldkF1\nZTY4aEpkN0Z2YTBqSU1WcldvN3loYzhnZXU3R0U5aG5DOTc0MlQybUc5bVU5bzRJeE" +
                "tuaWdKTzM4\nM0ltMzBlRkswNDl5eU5mVlBGcTNtZHZTV1FKbGRySkoyQU55SmpOOXdpMkxIa1FzTy9hOUNjTFBV\nUzVpWXNjdz" +
                "NtcjFtQVpiTlNsS25sMktsZnZoMi9wa0hFZnd0UDNJZXQ1ckQxc0lIRGhIeGhabW9I\nR0Jid3VvRGkyRngrVm9BdGxDWmFRREVB" +
                "V1F2dDFDc0poMnFKbDNoNGZkczVFUDUxZUczQkRzNDJa\nVT0KUHVibGljRXhwb25lbnQ6IEFRQUIKUHJpdmF0ZUV4cG9uZW50Oi" +
                "BOQ3VEbEE3S1FPNVd4ekpj\nK05kZUpqUXdka1BiUFl6RURjd2FxakJJT2pPWnovUXFCM1VadDBvYzVOeGJLcC92SXI4alJsTU9h" +
                "\neHFSWXhUS2V4TXZLNFB1d0RwUXJXZXF4QzF4NkZ1LzRkUWtnUTVsdUYwNkpBZ2JzVTBuSmhuQmVm\nTFNUUG41di84LzJkRVF6" +
                "eEM4NURFOEZLQTZ6Y0FXZ1ZCYVFUcEZ3S1QxK2k3UmhqNnVCbE5PdGxa\nSi9tWHNzY1pmZTZja1FGdVVIdmtDMTZKcnpRSEFyVC" +
                "tQK0FFMENhL3RqVTZkdEl2a0dxN0lWWEVD\nOTdHRzJWRXBwSUFLWmpaWGFwOEpOWmFrR2I1SENJV2U3d2hiTkZzYWRYZUdrWFM3" +
                "dThVWUIwbVhV\nQW8zNFZlWGRkbURiaFVoS0w2aXNVTWtCSjlUK0h4ckZpaFMvT2Rpd1E9PQpQcmltZTE6IEFPV002\nT210RUNW" +
                "Z1hWcjQvNHdweGZWa2Y2ak15ZUNEZVgwMUZtM21WOXBIbDJhL0lYb1lnS0JwU0hBZFFw\nUlZDeUNyT2pIMlh1d2wxRnVST2svVD" +
                "Y1MU5EMUFiRnlORk1KY3g4TGpLL2JYWU1vaG5aSlgwNTFX\naFBza1R1dFdDdmRsaGRZWWRsS2dUZThDMFN1akdXUWdtWVhXaDNJ" +
                "eG9HdzBDZ1BPaTNQbVoKUHJp\nbWUyOiBBTDBvR2pkakJMbUxvVjdXamZmdGdvaWdyNVVvUXROQXZ2Q3Z2YXVnWEVsaDdsV0orYk" +
                "Mw\nSVJZR3lBTTkwU2w5YzljNmZua1dtT0dWRlVwY2RGM1V2bVZiZ0xjZmd5Qk93NVl1YlVGOUQzK0pF\nOFRnZjlhU3BXdjU5cH" +
                "BSa01vZ0l3RXZCb2FRLzllSUhLc25qNmRpaThXempkNWEydTc4d2VrNUx1\nTTk0YlZkCkV4cG9uZW50MTogQUxNaVJuUnN1S2Zp" +
                "VHBmNnJqbnNNMjV2Y2V3QmZCejNuS3duN0l2\ndzhHZ0g3RStqSWZYTS96dXZUWGNvYmxlaEVpRDVYbDIwL0poZVlSemY5VmhzY1" +
                "hiOHR0RjFncFhr\nbmFtdnNFSVZMdTVrZS9MVWRMdE5OZVl1QWZnYXJIZUlJcTFzRDdHTWpnQ255N0F6UWkzcTgzbVhz\nZ0NCUX" +
                "NKVFQ2RFVibHN5b2kwcApFeHBvbmVudDI6IEV6ZDV3bHE2NWlhN1ZDa3o3eFlnQnNWY1VT\nanY4UXBJMjZiZS9BcWtsWWZ2Y0ow" +
                "OTBxZmFIS1MvdVNTVWxVUjdla2dsQlNRNGorSlhjV0NIS2Vr\ndEZwci9McHR5OU50TG4zeEQrVDA5VDlXYlBvc1lNYkpnUCtIN0" +
                "haNWZ1VWtlaFVaRHdYUUZxZDNZ\nRzhNL0l1QXVGdjJ3MGdBazhkYVgxSnZNbWFWMXdyaz0KQ29lZmZpY2llbnQ6IEN4TFJPOUgr" +
                "UUwv\nWHk5cmNYYUZmVnhZUkpsV0RYMW0vdVJ2QWZ0MVZnaFFRTzNFWnZzYnRlS3hrTEFNWXJta2c1ZFJy\nTkJ6T0NhVVM0VjlL" +
                "NmFxaHczVEZacEhkUDhNUHdLMG5zSTZaQnc4UGFmZ1ZIajk0MzhtRUt0NVBO\nSks1ZUh4dEg1aGtNNkVlbjhuZXpvcVg4WmEveV" +
                "BSd0J0U1ZvSW9pTXpJbVZIMD0K")
    var zsk1Public: String? =
        ("MIIBCgKCAQEAymLTAjeDfcIYUKyGqKhUrl/khgBJA9TNqrzZOfMmNFarbONxDnsd6WoHnqi5xGrNBV6ZGIGwc4tebG/XWBOVvI7P" +
                "b10ZHjm4muKnzA9Qt+TOwpukN/phOzDwFZx+QHHu18jePgmFstoSUJzb4baPSoLorCYbRKdIAHhSEALfL5LE8ByP/MwWCO6jD0wE" +
                "ZlzGsnow5wxnuVWhBt8FMpRN9FgrJ3YkfTxKz8IZpSx2yjf9IIa/lGvKxcUoAyrdWam14l3fBTI6tfx2nWv56L846wXjqtcZvQeO" +
                "0ewFdwNw2kWTT01kWeG6lXa1yo6CARkvVaF9zcrVNxWUm7CIvKibwQIDAQAB")
    var zsk1Private: String? =
        ("UHJpdmF0ZS1rZXktZm9ybWF0OiB2MS4yCkFsZ29yaXRobTogNSAoUlNBU0hBMSkKTW9kdWx1czog\nQU1waTB3STNnMzNDR0ZDc2" +
                "hxaW9WSzVmNUlZQVNRUFV6YXE4MlRuekpqUldxMnpqY1E1N0hlbHFC\nNTZvdWNScXpRVmVtUmlCc0hPTFhteHYxMWdUbGJ5T3oy" +
                "OWRHUjQ1dUpyaXA4d1BVTGZrenNLYnBE\nZjZZVHN3OEJXY2ZrQng3dGZJM2o0SmhiTGFFbENjMitHMmowcUM2S3dtRzBTblNBQj" +
                "RVaEFDM3kr\nU3hQQWNqL3pNRmdqdW93OU1CR1pjeHJKNk1PY01aN2xWb1FiZkJUS1VUZlJZS3lkMkpIMDhTcy9D\nR2FVc2Rzbz" +
                "MvU0NHdjVScnlzWEZLQU1xM1ZtcHRlSmQzd1V5T3JYOGRwMXIrZWkvT09zRjQ2clhH\nYjBIanRIc0JYY0RjTnBGazA5TlpGbmh1" +
                "cFYydGNxT2dnRVpMMVdoZmMzSzFUY1ZsSnV3aUx5b204\nRT0KUHVibGljRXhwb25lbnQ6IEFRQUIKUHJpdmF0ZUV4cG9uZW50Oi" +
                "BjL0dEb3d4OGx3b2Y5ZSt3\nd1dTV0JzUEczb0hCVXFHU0paTkUreTd5SHgvYjIyaTlOeWVQTGs1aENTTHVNYmxodFFiSnZFRm4v" +
                "\nZm5FNFdoOVBPY1RiRStBUlBOWDFYb0o2U21hS1l4SW1PRkxGakFtTnkrSWptbHQwV095N0dhWkJ5\nUDdOaDBzYkJjRVVReGI1" +
                "NFkzdHQ4aVBxVWlFQVdwZVNOMUY5a2RWVm5CY3UzU2UvMUlUVTZieEhC\nb3dqanNwRVY1NWExekpFZ2sySmg0dHFsQmxuZE1Va0" +
                "VEUXk5cmxwRGxPcmNVYkJVQ2lwdnM3TGhV\nM1oxdlhmRGFNVVZRRy9qbFFRMCs4bGZFY0ZYb3dPTWVUVTF2Tlduc1IvUWRjbzFZ" +
                "bEthUFZIVEor\nemlydUhOd3pGYlcxRFFhOU9TN2twZDRlQ0J5aDFZSWFvbWxhNkhRWlE9PQpQcmltZTE6IEFQT25D\nQStISFFI" +
                "WlUza2lYdkpoQlVwbWttalgxanhCclZaU3BYQ2lSZzZqZ2wybllENEpzVW9KZjhvZ0Yy\nbDc5TXlOZ09BdXN1bW45QUZNZDZFNz" +
                "N4MytpUW9ROEVkanpTckhvN2pFcFU1K1F1RERrdVlTdE5U\nRS9SeWtlVWM0V0w3ZG5iVCs1Zk5mWHl0NHljY21lMlMzbld6UGN5" +
                "MnRKODJ6T0NWN1hybUgKUHJp\nbWUyOiBBTlNrYnFxblhXNnRSemlOQWx6NDMzWHUrM2NkdTR3aWlKSkVDY21YRTUwTkpoc0FsWj" +
                "Bj\nUVVGKzMzM2hvbDAzbllZS0w3aUtqY3ZTblNkUlpYdVUvUm9ndmFrOXZtd0MwYzE3SmdrSWVUNWd3\nMVhMOXR5ZzBvSmR3US" +
                "tpSFNZWnRpRUZ2UWQyaVRFY1ZZMmp4SnptZnd6eC9EZkhvYWFuWXoyZTdy\nZEtmREozCkV4cG9uZW50MTogQU9uOWJQWk1rWVRu" +
                "Q3d6My95clNGZHlmcU5kYkFIa1pzYmVHNHk3\nS0NIR2lnVjRqa1gvQ215bzQrU3BKcTNJNEFwNDIvcDlNK2JEL0JJd0N2Wmh3QV" +
                "JPekVEUkVsQnhi\nTWZ0MWdqdTlUZXVWd2ZCTW9kU2wvbGVmWGx5VmI0c0FCK28wdG5ITm9WUWFNVEYwYklMKzU5MlBv\nSFdRdk" +
                "ZJUFNueDNNdUo4R2pvZApFeHBvbmVudDI6IE5UcFg3V1hwc0d1MW1VVks0eVQxSXR0SW5S\nK1hNOVB0SmRmY0wxY2U5TUEyMnFt" +
                "Qnlnc3BscENBdDAwRXpKckFWcTQwRmRpdzkrVm5xMURudTFD\nbkxxUWxkYmpiOHNPZDZ2YTVwYWMyV2xQMGNZU1Y3ZEpmLzN3aW" +
                "VKeFcrWk9VRUo0VUhFc2l1bVZp\nVVFzK2l4dnpqN1QvTXFOSkpxTmQ1eTNpaVlVZW82OD0KQ29lZmZpY2llbnQ6IGZFVzd1SzlD" +
                "aUM2\nVHJDUmttS2R0T3d4dnM1SDQ5MWdJRkQ3ckxIQ010T1d5enJxNXhGd0F1cmJFSktVdGlKQVVVNThE\nVUErcWZYTk5lODR4" +
                "RDZuWHNXRU9zckVENXFPWnBFT1BnczdxcHdaTHJXQ3BBcTRhQ2dtdGVKTk5K\nb0k5Rm41OFV0aCtXSUpBb2dnS0ZPSWd5WmsxUz" +
                "ZTRzFFZFBwWmd0dTQ5YnVpUT0K")
    var zsk2Public: String? =
        ("MIIBCgKCAQEAuCnKCdaJnVwi3Iu0zUYrciw05zZ3jXkZAKE+TOCT5/DlpGRNwzU8x0qc2niyt9Q+awSBpizFWAeAfgpoZXEjbIvw" +
                "hhDYC/Ze12jvDh/c/xyzaScnt6C6rlM2y1q7j7q9uI/64l7lQITOno4pvPZP3txXqll5VNfUjMbE3HNXL4O+qGLAij1/2clnS8wG" +
                "vokEITcYwOL6I0vBADqgu4pmu5sstMN+72Tz8FQ+ZAMQ8B7G2IlnPdTBypL0r0LjoFJ2SWZGnj/IRM7Am4iMiwlCSbbDzPibsMwl" +
                "MAquEBA1UVoN4PC1Ah+Hb6tP2h92Nl1JVSu0478uItF90dGl0gDQuwIDAQAB")
    var zsk2Private: String? =
        ("UHJpdmF0ZS1rZXktZm9ybWF0OiB2MS4yCkFsZ29yaXRobTogNSAoUlNBU0hBMSkKTW9kdWx1czog\nQUxncHlnbldpWjFjSXR5TH" +
                "RNMUdLM0lzTk9jMmQ0MTVHUUNoUGt6Z2srZnc1YVJrVGNNMVBNZEtu\nTnA0c3JmVVBtc0VnYVlzeFZnSGdINEthR1Z4STJ5TDhJ" +
                "WVEyQXYyWHRkbzd3NGYzUDhjczJrbko3\nZWd1cTVUTnN0YXU0KzZ2YmlQK3VKZTVVQ0V6cDZPS2J6MlQ5N2NWNnBaZVZUWDFJek" +
                "d4Tnh6Vnkr\nRHZxaGl3SW85ZjluSlowdk1CcjZKQkNFM0dNRGkraU5Md1FBNm9MdUtacnViTExURGZ1OWs4L0JV\nUG1RREVQQW" +
                "V4dGlKWnozVXdjcVM5SzlDNDZCU2RrbG1ScDQveUVUT3dKdUlqSXNKUWttMnc4ejRt\nN0RNSlRBS3JoQVFOVkZhRGVEd3RRSWZo" +
                "MityVDlvZmRqWmRTVlVydE9PL0xpTFJmZEhScGRJQTBM\ncz0KUHVibGljRXhwb25lbnQ6IEFRQUIKUHJpdmF0ZUV4cG9uZW50Oi" +
                "BZUm9Qb0M1Uk12cFFtMDdD\nVHZXdmxLeUFYR1RZbG15Q1pWV2ZYck9PNk4yWWg1d25jRVRTdmhXMkxHODFkRDNoaDFYQjhsZHEy" +
                "\naVppZHBLUVJ1Um5sQ1VZOWVkTnNzajlhc0MyUXhGRDJmTk5HYVYraUpDanZhUzhvRzQvdE9IMmhL\nak4xS3ZLWHNGbm5DeERE" +
                "NUlqdk5NYkdnUEJPSjN3UVNYUXlKZThJWjJFaTZDdThNeDQwcWZzQWFZ\na2phNFcySUJNVHNnSktnTnF1Nm1KUkNkelArQ3d6N3" +
                "pOL3RIWGZrMnBnYUVvNklXRWZ4RTNvVGN1\nbnJvVy8zMWkwLy9Kbjc4MzU1dEMxazJ6blZyNEorWHlKY09tazVKZGU0YTNpNElL" +
                "YXA3Kzg2M3Vs\nWHBwZElpeHMvWkhFSndnQy8xemxQdnpFK2lPYlgzSUo0eHZXRjc4eVE9PQpQcmltZTE6IEFPeC9u\nTmVCdWFN" +
                "bFVUK0tKMkZuWHV0SUtpeUt1UU02S2VEaWlyVW5WekMxUExLSnFCZktQc3RuM28yQ3ky\nWmVrR01hZ0g2TWtJU0RCNG5mSEtHVE" +
                "RMNHhpVHFBSVV2dHZkcDN3c3h3TTYyMkRPUFA1T2dodU1t\nbU12WHowU21Ra3NxaHBCVWcveEcyMjR2dWQxN09JVFJIdGZUa1Ay" +
                "LytnWXhOQS81NkJuTnYKUHJp\nbWUyOiBBTWRaWnBEOHdUalNMOGpNTnNUcXIwcExpa0RjT3RkbTdJc3ZRQzI5S2VOZm1zalg0Q2" +
                "pH\nb2dTMFhsN1dDbC9QM2FOVE1Sd1BSRWo0Z1hSeWZSVkI0Qm1NUEsyMmMzVXFIV0lFT1F0M0FKK1Ew\nOThBYVVUWTdlUnlDdE" +
                "xNVmRvMUlTQW1kMVBXMC9GOWt0S3QxcjdEakVSeTVKWjVmVjZhQzlqcHFs\naDlQMkYxCkV4cG9uZW50MTogSmpOdXlEakNIYUkz" +
                "ZjRYM1ducHlFSnMxaE1CYnBqUE04L0JqNjJN\nUmFHSytEVERESUNTaEJBWWhJc2FQeUZkYUIwZ09uV0pqTGFSa29WYVNyMTZyWG" +
                "tWdUh5NzNNZWFU\nRmlqbUpGT3paVW5VeHkrYmtBdHlaOUFheHpPOUEzUEE0azQ5ek5QcG41amoyYlNKUDUwWUsvT3ha\nbjgvNS" +
                "8wNk84bHM3YndVTXEwPQpFeHBvbmVudDI6IEF5d3VFTEtsRkpWcncwTlV2OCtJV0tOWklP\nMGdWcHRCdW5sWENTTlFqbGpRS3RB" +
                "Y3RpbHJISzRPTG00em5oQzkwMEtZcm55anRwR1Y1ZGVJbkJU\nL1R3akhVbkJyNS9weW0wTzc4SzNGS2NUeFNrUkVhSjhYTWlLS2" +
                "Y0NVc4M0VUQk9MMzFUSy9Ec0hU\nMjF4KzVXeFJTQ21YNzdraFFLY3M5eGZJNlQyZEM1MD0KQ29lZmZpY2llbnQ6IEFOSm8rWXNX" +
                "S01E\nM2JJWUI2NnhSUXBrRW90OUJqRmdhS0FuWmhJUVFlV1dSMlZZUFNscU9xbzRHWXArUmRZOFpyVStW\nME8wWE82MjNkWG03" +
                "Rk0ybllPNUJSUnBJSTNDUHM3bjk4N2czMmJCUTZqdHVRV2xsV0s0L3VQUEtK\nMjFtRVpka2l1RHoxVzFWRkNoVzhsaUNFWDNQZl" +
                "ZtK01qblJxQk1UMDUreUNUWlkK")

    // example.com.		315360000	IN	SOA	ns1.example.com. admin.example.com. 2016091400 86400 3600 1814400 259200
    var postZoneRecord0: String? =
        "B2V4YW1wbGUDY29tAAAGAAESzAMAADgDbnMxB2V4YW1wbGUDY29tAAVhZG1pbgdleGFtcGxlA2NvbQB4Kx0IAAFRgAAADhAAG6+A" +
                "AAP0gA=="

    // example.com.		315360000	IN	NS	ns1.example.com.
    var postZoneRecord3: String? = "B2V4YW1wbGUDY29tAAACAAESzAMAABEDbnMxB2V4YW1wbGUDY29tAA=="

    // example.com.		315360000	IN	NS	ns2.example.com.
    var postZoneRecord4: String? = "B2V4YW1wbGUDY29tAAACAAESzAMAABEDbnMyB2V4YW1wbGUDY29tAA=="

    // example.com.		259200	IN	NSEC	ftp.example.com. NS SOA RRSIG NSEC
    var postZoneRecord7: String? = "B2V4YW1wbGUDY29tAAAvAAEAA/SAABkDZnRwB2V4YW1wbGUDY29tAAAGIgAAAAAD"

    // ftp.example.com.	1814400	IN	A	12.34.56.78
    var postZoneRecord10: String? = "A2Z0cAdleGFtcGxlA2NvbQAAAQABABuvgAAEDCI4Tg=="

    // ftp.example.com.	1814400	IN	A	21.43.65.87
    var postZoneRecord11: String? = "A2Z0cAdleGFtcGxlA2NvbQAAAQABABuvgAAEFStBVw=="

    // ftp.example.com.	259200	IN	AAAA	2001:db8:0:0:12:34:56:78
    var postZoneRecord14: String? = "A2Z0cAdleGFtcGxlA2NvbQAAHAABAAP0gAAQIAENuAAAAAAAEgA0AFYAeA=="

    // ftp.example.com.	259200	IN	AAAA	2001:db8:0:0:21:43:65:87
    var postZoneRecord15: String? = "A2Z0cAdleGFtcGxlA2NvbQAAHAABAAP0gAAQIAENuAAAAAAAIQBDAGUAhw=="

    // ftp.example.com.	259200	IN	NSEC	mirror.ftp.example.com. A AAAA RRSIG NSEC
    var postZoneRecord18: String? = "A2Z0cAdleGFtcGxlA2NvbQAALwABAAP0gAAgBm1pcnJvcgNmdHAHZXhhbXBsZQNjb20AAAZAAAAIAAM="

    // mirror.ftp.example.com.	315360000	IN	CNAME	ftp.example.com.
    var postZoneRecord21: String? = "Bm1pcnJvcgNmdHAHZXhhbXBsZQNjb20AAAUAARLMAwAAEQNmdHAHZXhhbXBsZQNjb20A"

    // mirror.ftp.example.com.	259200	IN	NSEC	www.example.com. CNAME RRSIG NSEC
    var postZoneRecord24: String? = "Bm1pcnJvcgNmdHAHZXhhbXBsZQNjb20AAC8AAQAD9IAAGQN3d3cHZXhhbXBsZQNjb20AAAYEAAAAAAM="

    // www.example.com.	1814400	IN	A	11.22.33.44
    var postZoneRecord27: String? = "A3d3dwdleGFtcGxlA2NvbQAAAQABABuvgAAECxYhLA=="

    // www.example.com.	1814400	IN	A	55.66.77.88
    var postZoneRecord28: String? = "A3d3dwdleGFtcGxlA2NvbQAAAQABABuvgAAEN0JNWA=="

    // www.example.com.	315360000	IN	TXT	"dead0123456789"
    var postZoneRecord31: String? = "A3d3dwdleGFtcGxlA2NvbQAAEAABEswDAAAPDmRlYWQwMTIzNDU2Nzg5"

    // www.example.com.	259200	IN	AAAA	2001:db8:0:0:4:3:2:1
    var postZoneRecord34: String? = "A3d3dwdleGFtcGxlA2NvbQAAHAABAAP0gAAQIAENuAAAAAAABAADAAIAAQ=="

    // www.example.com.	259200	IN	AAAA	2001:db8:0:0:5:6:7:8
    var postZoneRecord35: String? = "A3d3dwdleGFtcGxlA2NvbQAAHAABAAP0gAAQIAENuAAAAAAABQAGAAcACA=="

    // www.example.com.	259200	IN	NSEC	mirror.www.example.com. A TXT AAAA RRSIG NSEC
    var postZoneRecord38: String? = "A3d3dwdleGFtcGxlA2NvbQAALwABAAP0gAAgBm1pcnJvcgN3d3cHZXhhbXBsZQNjb20AAAZAAIAIAAM="

    // mirror.www.example.com.	315360000	IN	CNAME	www.example.com.
    var postZoneRecord41: String? = "Bm1pcnJvcgN3d3cHZXhhbXBsZQNjb20AAAUAARLMAwAAEQN3d3cHZXhhbXBsZQNjb20A"

    // mirror.www.example.com.	259200	IN	NSEC	example.com. CNAME RRSIG NSEC
    var postZoneRecord44: String? = "Bm1pcnJvcgN3d3cHZXhhbXBsZQNjb20AAC8AAQAD9IAAFQdleGFtcGxlA2NvbQAABgQAAAAAAw=="

    // example.com.		315360000	IN	SOA	ns1.example.com. admin.example.com. 2016091400 86400 3600 1814400 259200
    var signedRecord0: String? =
        "B2V4YW1wbGUDY29tAAAGAAESzAMAADgDbnMxB2V4YW1wbGUDY29tAAVhZG1pbgdleGFtcGxlA2NvbQB4Kx0IAAFRgAAADhAAG6+A" +
                "AAP0gA=="

    // example.com.		315360000	IN	RRSIG	SOA 5 2 315360000 20260901000000 20160901000000 62715 example.com. C0hpMqwBTBad15gPKdGAnz1xY/yoKVQMwZJPPZAUUV6finNeX8aS9wtml83eWslfuP+ipw2Xf1/EV2umUEHbtptJR0SReeR6NeUtHM2QAYoUG1+OmqQqebM5cD8pgyry1BAP+9wm9IKmyS3dSKDrj9M3Gd7TlUZrux96N/29KOPrLgb7Chbr0wqrTOJIQgV0DSecYoayM/iuQXO6qDAPc64W3jiBBPkLVub7NK1O9VIHHLPw1ElZVuK/8muxrsxp7eDzsjkNnVrxlat+1QQECk+uvARHxrY7eTGW+srZGyrkwDm2EvBAbmx3U0SaUPQDGzySeOZE9qdu7yYi0PcRsw==
    var signedRecord1: String? =
        ("B2V4YW1wbGUDY29tAAAuAAESzAMAAR8ABgUCEswDAGqWFYBXx2+A9PsHZXhhbXBsZQNjb20AC0hpMqwBTBad15gPKdGAnz1xY/yo" +
                "KVQMwZJPPZAUUV6finNeX8aS9wtml83eWslfuP+ipw2Xf1/EV2umUEHbtptJR0SReeR6NeUtHM2QAYoUG1+OmqQqebM5cD8pgyry" +
                "1BAP+9wm9IKmyS3dSKDrj9M3Gd7TlUZrux96N/29KOPrLgb7Chbr0wqrTOJIQgV0DSecYoayM/iuQXO6qDAPc64W3jiBBPkLVub7" +
                "NK1O9VIHHLPw1ElZVuK/8muxrsxp7eDzsjkNnVrxlat+1QQECk+uvARHxrY7eTGW+srZGyrkwDm2EvBAbmx3U0SaUPQDGzySeOZE" +
                "9qdu7yYi0PcRsw==")

    // example.com.		315360000	IN	RRSIG	SOA 5 2 315360000 20260901000000 20160901000000 62715 example.com. oblQvApzPjS/Frig1WwkS8XAVR4s8Yve+/DC8UV6JysMPW0PrwMEDrwjSPoxwGkAySYXuRLSJdW1H6HPXTWjJBontjCnCpiFEu2gnqacZi0HgVPEFGwWEH1lTVdglBrz5Px7CBnqzvVopzn9am72jVzmYh4JKgcEqCQnz4mA8f35DW6gkq52ECB1mR5nyckf6swZ8V63Ypu/aNcFrPnqQWWyfNS3dEormvNiahXtqEv0Keg5GeaYS3e9bu8DQofPojZbyZlk+GsNTfJJGLOkcG7SZliGSfAQRUMUb3D9NIPgfOWCAdAR286TAOm16ZoS/d06VoTNeUg3+n8D/YqzgA==
    var signedRecord2: String? =
        ("B2V4YW1wbGUDY29tAAAuAAESzAMAAR8ABgUCEswDAGqWFYBXx2+A9PsHZXhhbXBsZQNjb20AoblQvApzPjS/Frig1WwkS8XAVR4s" +
                "8Yve+/DC8UV6JysMPW0PrwMEDrwjSPoxwGkAySYXuRLSJdW1H6HPXTWjJBontjCnCpiFEu2gnqacZi0HgVPEFGwWEH1lTVdglBrz" +
                "5Px7CBnqzvVopzn9am72jVzmYh4JKgcEqCQnz4mA8f35DW6gkq52ECB1mR5nyckf6swZ8V63Ypu/aNcFrPnqQWWyfNS3dEormvNi" +
                "ahXtqEv0Keg5GeaYS3e9bu8DQofPojZbyZlk+GsNTfJJGLOkcG7SZliGSfAQRUMUb3D9NIPgfOWCAdAR286TAOm16ZoS/d06VoTN" +
                "eUg3+n8D/YqzgA==")

    // example.com.		315360000	IN	NS	ns1.example.com.
    var signedRecord3: String? = "B2V4YW1wbGUDY29tAAACAAESzAMAABEDbnMxB2V4YW1wbGUDY29tAA=="

    // example.com.		315360000	IN	NS	ns2.example.com.
    var signedRecord4: String? = "B2V4YW1wbGUDY29tAAACAAESzAMAABEDbnMyB2V4YW1wbGUDY29tAA=="

    // example.com.		315360000	IN	RRSIG	NS 5 2 315360000 20260901000000 20160901000000 62715 example.com. VXb0w+5JGUpmuLhH9IQwiF/TwlDIrCwovLV4/gIhXd7CmwkbqUkSwjPtuHvs7LmqlzzCfmAvqJxRb4aKgVFaA+ltlmdoQPR3fRTJM5Vt8bfA7o9/e8Epxu7U6q3uulgPY+PfmrgS6zclCTcb4Llxzmmi6COvN8xVBe4z9oe2+vDp+o14VpmvkLQNhlIIrcDUyPwYqBuYTT77lTm/DeXs47Afzp8r9JPgoTfu+NgvkSCK2RXgubIh73kdPaX222Hon7XEutA+nMi//1J3Tq+96dqZ9l4sawd3EzBL7fM+ANPHEZd4ducRRnzAXOCI//2sa4XWKZubVtuZCYZoyj12sw==
    var signedRecord5: String? =
        ("B2V4YW1wbGUDY29tAAAuAAESzAMAAR8AAgUCEswDAGqWFYBXx2+A9PsHZXhhbXBsZQNjb20AVXb0w+5JGUpmuLhH9IQwiF/TwlDI" +
                "rCwovLV4/gIhXd7CmwkbqUkSwjPtuHvs7LmqlzzCfmAvqJxRb4aKgVFaA+ltlmdoQPR3fRTJM5Vt8bfA7o9/e8Epxu7U6q3uulgP" +
                "Y+PfmrgS6zclCTcb4Llxzmmi6COvN8xVBe4z9oe2+vDp+o14VpmvkLQNhlIIrcDUyPwYqBuYTT77lTm/DeXs47Afzp8r9JPgoTfu" +
                "+NgvkSCK2RXgubIh73kdPaX222Hon7XEutA+nMi//1J3Tq+96dqZ9l4sawd3EzBL7fM+ANPHEZd4ducRRnzAXOCI//2sa4XWKZub" +
                "VtuZCYZoyj12sw==")

    // example.com.		315360000	IN	RRSIG	NS 5 2 315360000 20260901000000 20160901000000 62715 example.com. pHMxn1FV1Vb4/Y0nO4sjBSolQIFBUpgUodq9q2MHlpqA9cY4kQAvF0oDONfvqdT8a7BCwrzDtSbLAi0QJuU0wao6JKM3Kr4CuVWPAaPCX4gdKi5Gye1F2A+O5NKC8aBweYxxEg6AP5ADyjkWYX0k7XW4c4kVkBmToYroO0kgaPQyj7AW4iznmRcXjnM4nufRjSwEfIYzIlQZ2Pf4ftaKH58DoAeouTDr0xYVM9F9q+78E3ZSgBJNprnOi5zo7qQcifIgshuD06cUjQ7e2Sgi1wEBUG3nyTa4qu8D7fK9oRJHIqAGeUKCAsWlC675HTdrGKBwbrjM41WoQkmCcclZNg==
    var signedRecord6: String? =
        ("B2V4YW1wbGUDY29tAAAuAAESzAMAAR8AAgUCEswDAGqWFYBXx2+A9PsHZXhhbXBsZQNjb20ApHMxn1FV1Vb4/Y0nO4sjBSolQIFB" +
                "UpgUodq9q2MHlpqA9cY4kQAvF0oDONfvqdT8a7BCwrzDtSbLAi0QJuU0wao6JKM3Kr4CuVWPAaPCX4gdKi5Gye1F2A+O5NKC8aBw" +
                "eYxxEg6AP5ADyjkWYX0k7XW4c4kVkBmToYroO0kgaPQyj7AW4iznmRcXjnM4nufRjSwEfIYzIlQZ2Pf4ftaKH58DoAeouTDr0xYV" +
                "M9F9q+78E3ZSgBJNprnOi5zo7qQcifIgshuD06cUjQ7e2Sgi1wEBUG3nyTa4qu8D7fK9oRJHIqAGeUKCAsWlC675HTdrGKBwbrjM" +
                "41WoQkmCcclZNg==")

    // example.com.		259200	IN	NSEC	ftp.example.com. NS SOA RRSIG NSEC
    var signedRecord7: String? = "B2V4YW1wbGUDY29tAAAvAAEAA/SAABkDZnRwB2V4YW1wbGUDY29tAAAGIgAAAAAD"

    // example.com.		259200	IN	RRSIG	NSEC 5 2 259200 20260901000000 20160901000000 62715 example.com. s7BUvoJ21W5HV9Pqg0kUpMa2FmgZrCgwYBThxIbH1ZtiXRR1Q4BzxMdxypi8WetbDITNL4gnWI4BBnww2WduwO94tsTErNMrMShopZKaPn17LB2sMF4QatkfEO85OUYSUWAhtWFzRnTpdJT0UADlAmS+iE/QVGYCTJtZh15cMhPDQM8vMcXfYWrBWTe7iLHjJYMgqsXUI2RnF0qA6dx7/55rUGqC8sEk9mc8NP8bz0cuDLAplkCbG02slurXya5KnOq9LT4Gmt7ovQ6yCDs/h6S3Hbuo5mbDhK6vUPY7Ef2BSRylJlUOJZmr+p7zK9pPn2/0GBVu8rryyM7pWlheGw==
    var signedRecord8: String? =
        ("B2V4YW1wbGUDY29tAAAuAAEAA/SAAR8ALwUCAAP0gGqWFYBXx2+A9PsHZXhhbXBsZQNjb20As7BUvoJ21W5HV9Pqg0kUpMa2FmgZ" +
                "rCgwYBThxIbH1ZtiXRR1Q4BzxMdxypi8WetbDITNL4gnWI4BBnww2WduwO94tsTErNMrMShopZKaPn17LB2sMF4QatkfEO85OUYS" +
                "UWAhtWFzRnTpdJT0UADlAmS+iE/QVGYCTJtZh15cMhPDQM8vMcXfYWrBWTe7iLHjJYMgqsXUI2RnF0qA6dx7/55rUGqC8sEk9mc8" +
                "NP8bz0cuDLAplkCbG02slurXya5KnOq9LT4Gmt7ovQ6yCDs/h6S3Hbuo5mbDhK6vUPY7Ef2BSRylJlUOJZmr+p7zK9pPn2/0GBVu" +
                "8rryyM7pWlheGw==")

    // example.com.		259200	IN	RRSIG	NSEC 5 2 259200 20260901000000 20160901000000 62715 example.com. o7X99MS6Px64FfsDU+r/oC2dyq2g78RBVQA+xbbXk4NFwdoch4/eyTur3VqCGn8umriHTPIN0URY5U0nzod0SAQyT7daRlC7qnwJPpmBP5MYuOI19DoFJuqJ6Wvwu1r4xcv886tEYh68H4YRmNB2Pso34pC8An9WmUW/sKiQwb0tC4Xmcj9AMfKDgLeiWBTJEBHWqrtagywS1gvvq5VRQaz5k0K9Vb0aIimPOce/twVNpbrysNsZoR1a243msF9bUUtg5It9ZDYeCleQgtzJyBpxWcEfhWd28PSDu280ymGmyeICznrJuwg8KNB2Byh9pxNGsaw1gBxu/2hj88OT+g==
    var signedRecord9: String? =
        ("B2V4YW1wbGUDY29tAAAuAAEAA/SAAR8ALwUCAAP0gGqWFYBXx2+A9PsHZXhhbXBsZQNjb20Ao7X99MS6Px64FfsDU+r/oC2dyq2g" +
                "78RBVQA+xbbXk4NFwdoch4/eyTur3VqCGn8umriHTPIN0URY5U0nzod0SAQyT7daRlC7qnwJPpmBP5MYuOI19DoFJuqJ6Wvwu1r4" +
                "xcv886tEYh68H4YRmNB2Pso34pC8An9WmUW/sKiQwb0tC4Xmcj9AMfKDgLeiWBTJEBHWqrtagywS1gvvq5VRQaz5k0K9Vb0aIimP" +
                "Oce/twVNpbrysNsZoR1a243msF9bUUtg5It9ZDYeCleQgtzJyBpxWcEfhWd28PSDu280ymGmyeICznrJuwg8KNB2Byh9pxNGsaw1" +
                "gBxu/2hj88OT+g==")

    // ftp.example.com.	1814400	IN	A	12.34.56.78
    var signedRecord10: String? = "A2Z0cAdleGFtcGxlA2NvbQAAAQABABuvgAAEDCI4Tg=="

    // ftp.example.com.	1814400	IN	A	21.43.65.87
    var signedRecord11: String? = "A2Z0cAdleGFtcGxlA2NvbQAAAQABABuvgAAEFStBVw=="

    // ftp.example.com.	1814400	IN	RRSIG	A 5 3 1814400 20260901000000 20160901000000 62715 example.com. E4nQ6QA1Q0knYeCevRX3nxWWOUkfZ2ZiVzxkFBplIZpegVqLBNsIbeh/2kjuFHm7TX9fENiTC/uRfcVOq9sEb3gaBaD88gqqzKZ4qhWOPU0gnIQARa45K0GatP3WPgL8eeHhnZF9diIdK5M/sX6XxRte0bJry5hi4HFkDbXmlsWjvNMyrlrCmw/AWYYGI4kqR8jUKf4BJIdT7DQ+3Ch4MDcEU2PfO1R6iHwfWUVeGRRg+aw8Uqht1S/Cx+fo44kowicV4o3+RwTWRW6UbWvF5b5vjLjByIzg8MvzCcGPnUdwKKfZBLT1WC02LXVRa/19C3RKgHzK5AGFeRWiKs9PNA==
    var signedRecord12: String? =
        ("A2Z0cAdleGFtcGxlA2NvbQAALgABABuvgAEfAAEFAwAbr4BqlhWAV8dvgPT7B2V4YW1wbGUDY29tABOJ0OkANUNJJ2Hgnr0V958V" +
                "ljlJH2dmYlc8ZBQaZSGaXoFaiwTbCG3of9pI7hR5u01/XxDYkwv7kX3FTqvbBG94GgWg/PIKqsymeKoVjj1NIJyEAEWuOStBmrT9" +
                "1j4C/Hnh4Z2RfXYiHSuTP7F+l8UbXtGya8uYYuBxZA215pbFo7zTMq5awpsPwFmGBiOJKkfI1Cn+ASSHU+w0PtwoeDA3BFNj3ztU" +
                "eoh8H1lFXhkUYPmsPFKobdUvwsfn6OOJKMInFeKN/kcE1kVulG1rxeW+b4y4wciM4PDL8wnBj51HcCin2QS09VgtNi11UWv9fQt0" +
                "SoB8yuQBhXkVoirPTzQ=")

    // ftp.example.com.	1814400	IN	RRSIG	A 5 3 1814400 20260901000000 20160901000000 62715 example.com. nZusmWoKUBqvWAbKIqU6rvZgWQt3Sdzy7Xi9NMpJKfhfZdyE87cfsxPDf6/PHOy2+bPxHri2dE5OsPkWsdLbtRNvpne+Gh8L4cO8VdgbfhnAlI/9VGn3r8cwNHyuiYjH02Rqpm2HZHCi7rnUHJmwxvQNfUyIXETOpyR4lDLGEG6FeDuojNm9fw68cosROASn0Zlzk0991MbqMhi7TUsIfgJAWZQc9Ttzr5CORtSyA19gZMCJMiEJuU5jJXJpizC13DiUEKpWDTfYB8PnDd+lh0oTKAWc1dmFl3pNRuU9CUIABQRHidoDVCjr+6N5j4BkPWQnG/CrVhKerRbUtU3uWQ==
    var signedRecord13: String? =
        ("A2Z0cAdleGFtcGxlA2NvbQAALgABABuvgAEfAAEFAwAbr4BqlhWAV8dvgPT7B2V4YW1wbGUDY29tAJ2brJlqClAar1gGyiKlOq72" +
                "YFkLd0nc8u14vTTKSSn4X2XchPO3H7MTw3+vzxzstvmz8R64tnROTrD5FrHS27UTb6Z3vhofC+HDvFXYG34ZwJSP/VRp96/HMDR8" +
                "romIx9NkaqZth2Rwou651ByZsMb0DX1MiFxEzqckeJQyxhBuhXg7qIzZvX8OvHKLETgEp9GZc5NPfdTG6jIYu01LCH4CQFmUHPU7" +
                "c6+QjkbUsgNfYGTAiTIhCblOYyVyaYswtdw4lBCqVg032AfD5w3fpYdKEygFnNXZhZd6TUblPQlCAAUER4naA1Qo6/ujeY+AZD1k" +
                "Jxvwq1YSnq0W1LVN7lk=")

    // ftp.example.com.	259200	IN	AAAA	2001:db8:0:0:12:34:56:78
    var signedRecord14: String? = "A2Z0cAdleGFtcGxlA2NvbQAAHAABAAP0gAAQIAENuAAAAAAAEgA0AFYAeA=="

    // ftp.example.com.	259200	IN	AAAA	2001:db8:0:0:21:43:65:87
    var signedRecord15: String? = "A2Z0cAdleGFtcGxlA2NvbQAAHAABAAP0gAAQIAENuAAAAAAAIQBDAGUAhw=="

    // ftp.example.com.	259200	IN	RRSIG	AAAA 5 3 259200 20260901000000 20160901000000 62715 example.com. dZlga09LK/1VFHmE1RSU6JgOk5z6Aa8lrT9gT7/1srtXFauFvhe2dEtaIC8wWuv05ee1Hv+f5xd4YrQyXSUj3Fb+sGRWG6Uo9qAH8BJ4J8ckxWztL61pxlQGDKEblA8nq8/mRKXLmgpkcag8SIFr1BlgPaEP0eRjxdyBlBHb5R9KCEkw9ypzaF8AKrLWmbJFNIAqCryiihFNg0hdft/dGKwr6rKj5T2p1Cs8k5eLT2AtW2w2BsoTGr4ndYdZv7Qok94MQZ21GnOhSrLxf8mwz0uWhUEZPvR1Ef4WOeAsF+NrEuMhkAxIEv/tze4YKbnigCeolMB9oryO11aFu4UJKA==
    var signedRecord16: String? =
        ("A2Z0cAdleGFtcGxlA2NvbQAALgABAAP0gAEfABwFAwAD9IBqlhWAV8dvgPT7B2V4YW1wbGUDY29tAHWZYGtPSyv9VRR5hNUUlOiY" +
                "DpOc+gGvJa0/YE+/9bK7VxWrhb4XtnRLWiAvMFrr9OXntR7/n+cXeGK0Ml0lI9xW/rBkVhulKPagB/ASeCfHJMVs7S+tacZUBgyh" +
                "G5QPJ6vP5kSly5oKZHGoPEiBa9QZYD2hD9HkY8XcgZQR2+UfSghJMPcqc2hfACqy1pmyRTSAKgq8oooRTYNIXX7f3RisK+qyo+U9" +
                "qdQrPJOXi09gLVtsNgbKExq+J3WHWb+0KJPeDEGdtRpzoUqy8X/JsM9LloVBGT70dRH+FjngLBfjaxLjIZAMSBL/7c3uGCm54oAn" +
                "qJTAfaK8jtdWhbuFCSg=")

    // ftp.example.com.	259200	IN	RRSIG	AAAA 5 3 259200 20260901000000 20160901000000 62715 example.com. tLOODQVeROFNZQeaptvs+i+8lRyx2GCx4aAXIHf5LfeIwubuJxe+nwbSjYWmO5vDLmt1Eyl4ovcnTTyQig5ZUlFjaCiS/LxhGAHAQ0azBPaBCtOJi8i6ptTq0yz/9Bksq23UxWvM7VmAsj/1rTWy+ohUcqHEzDdc4HDeaChGRAOuw9OqeUNpS6uLLtfBVWe3XdoHPPVQz6y4AFHpyzfTts3cHl+97BLDAzDsVdiQiJwMVzh/3xinGqeu/Z2NRkqarRb0vqPB+nPgRAGBXPVT3nzAjaQ8Tuo4VWCqXTwtncoveXAZ4ejmMG9yubWeKy4LCDQ/Drd/TppBYYo/BcbvMg==
    var signedRecord17: String? =
        ("A2Z0cAdleGFtcGxlA2NvbQAALgABAAP0gAEfABwFAwAD9IBqlhWAV8dvgPT7B2V4YW1wbGUDY29tALSzjg0FXkThTWUHmqbb7Pov" +
                "vJUcsdhgseGgFyB3+S33iMLm7icXvp8G0o2Fpjubwy5rdRMpeKL3J008kIoOWVJRY2gokvy8YRgBwENGswT2gQrTiYvIuqbU6tMs" +
                "//QZLKtt1MVrzO1ZgLI/9a01svqIVHKhxMw3XOBw3mgoRkQDrsPTqnlDaUuriy7XwVVnt13aBzz1UM+suABR6cs307bN3B5fvewS" +
                "wwMw7FXYkIicDFc4f98Ypxqnrv2djUZKmq0W9L6jwfpz4EQBgVz1U958wI2kPE7qOFVgql08LZ3KL3lwGeHo5jBvcrm1nisuCwg0" +
                "Pw63f06aQWGKPwXG7zI=")

    // ftp.example.com.	259200	IN	NSEC	mirror.ftp.example.com. A AAAA RRSIG NSEC
    var signedRecord18: String? = "A2Z0cAdleGFtcGxlA2NvbQAALwABAAP0gAAgBm1pcnJvcgNmdHAHZXhhbXBsZQNjb20AAAZAAAAIAAM="

    // ftp.example.com.	259200	IN	RRSIG	NSEC 5 3 259200 20260901000000 20160901000000 62715 example.com. JAqwuvRx7rYuMiAJZ7IeOiFi3wg5QxSY0/CV4J3dR8TiTkeSNvbSs6iHV3ysyIXIHlAn9elUyfpree6wCrNTgCFCpuZ4XX9nJaBf9Y5mTo0rOStOWB+/xuQfdt71LN4Z1OpTwjptjDlU3+k/h4aQUPJGpfUf6qtvuvDuF+a6/0P0dY03XCWrc2oGme8uHHKXp5M5c3UzMCD4FEx3E5aBk9ZHYx4lSY/TAUeTGfCMCPMUjfDxZbXfKL6NX7RZDZomFXHTFsEL37lAgDIL834MKPhkhGbJcefL8kuT4gAhXhQsZSQy/VL61arYWFQ55i7a8hz97opFyHjNKaezN0C/7A==
    var signedRecord19: String? =
        ("A2Z0cAdleGFtcGxlA2NvbQAALgABAAP0gAEfAC8FAwAD9IBqlhWAV8dvgPT7B2V4YW1wbGUDY29tACQKsLr0ce62LjIgCWeyHjoh" +
                "Yt8IOUMUmNPwleCd3UfE4k5Hkjb20rOoh1d8rMiFyB5QJ/XpVMn6a3nusAqzU4AhQqbmeF1/ZyWgX/WOZk6NKzkrTlgfv8bkH3be" +
                "9SzeGdTqU8I6bYw5VN/pP4eGkFDyRqX1H+qrb7rw7hfmuv9D9HWNN1wlq3NqBpnvLhxyl6eTOXN1MzAg+BRMdxOWgZPWR2MeJUmP" +
                "0wFHkxnwjAjzFI3w8WW13yi+jV+0WQ2aJhVx0xbBC9+5QIAyC/N+DCj4ZIRmyXHny/JLk+IAIV4ULGUkMv1S+tWq2FhUOeYu2vIc" +
                "/e6KRch4zSmnszdAv+w=")

    // ftp.example.com.	259200	IN	RRSIG	NSEC 5 3 259200 20260901000000 20160901000000 62715 example.com. kyMPSnQ/SpCrep6Vm3/CODGqQs2LCHEMu+He3pXxn5HnNriI8U8S4va77epIozhZN016EIkXxe1XTJLezzxQy2G9RPP5rf2I3/nPdFV0GiV/e/ah00xuWw57ZQb1Qf9MO5+Qbe09J4P39Afxx8jbRaLQ/QJmcJOxr0CF3zJyBoHYco62bro9kKSOxECuQ92J9rI6EVGlAVPF5prOVhXcWPP78p6Xk80qN6gFNnaIkLs9KvLQ+arF5G3N5I5LNRHnck22fR14gJGf127bmjAp5m3oFLET1FH0Gl5m+r+zp49UUZGPfLXkhPvkMjBbraFGTUGujryroCrKw2OYlNMY8A==
    var signedRecord20: String? =
        ("A2Z0cAdleGFtcGxlA2NvbQAALgABAAP0gAEfAC8FAwAD9IBqlhWAV8dvgPT7B2V4YW1wbGUDY29tAJMjD0p0P0qQq3qelZt/wjgx" +
                "qkLNiwhxDLvh3t6V8Z+R5za4iPFPEuL2u+3qSKM4WTdNehCJF8XtV0yS3s88UMthvUTz+a39iN/5z3RVdBolf3v2odNMblsOe2UG" +
                "9UH/TDufkG3tPSeD9/QH8cfI20Wi0P0CZnCTsa9Ahd8ycgaB2HKOtm66PZCkjsRArkPdifayOhFRpQFTxeaazlYV3Fjz+/Kel5PN" +
                "KjeoBTZ2iJC7PSry0PmqxeRtzeSOSzUR53JNtn0deICRn9du25owKeZt6BSxE9RR9BpeZvq/s6ePVFGRj3y15IT75DIwW62hRk1B" +
                "ro68q6AqysNjmJTTGPA=")

    // mirror.ftp.example.com.	315360000	IN	CNAME	ftp.example.com.
    var signedRecord21: String? = "Bm1pcnJvcgNmdHAHZXhhbXBsZQNjb20AAAUAARLMAwAAEQNmdHAHZXhhbXBsZQNjb20A"

    // mirror.ftp.example.com.	315360000	IN	RRSIG	CNAME 5 4 315360000 20260901000000 20160901000000 62715 example.com. l4MKlMYFR997yhH5ltWPzTUjxjZbQnCE1/cH7pBBi0Ha4zuvfbHiDXc4b1KwgYUXfn7ONUvF9OJtAC6f45jHUVeN+GxdoDs0JOKkXpE8g8z41HcXCDvThhLg2omtWS9QJRUs23B2NnfYRCGzpwCmeWQX1jcOfUncOLDTqlfuKf6b6KQUiHQA/2bff4iwuN+UXlKqGi2kVRTEd94K/AoM1C7z13KzPuFMO8HUhjUjVZu+cJ8o8sqe3LgBCmxWdzh7BB/rZNC9wMi3ZsLfoStWjHFWrrJdQntB1dEX9fLoegVFbljNAR639dkSONA4AeKoS5LdqDQwANb1TbHueUYLDQ==
    var signedRecord22: String? =
        ("Bm1pcnJvcgNmdHAHZXhhbXBsZQNjb20AAC4AARLMAwABHwAFBQQSzAMAapYVgFfHb4D0+wdleGFtcGxlA2NvbQCXgwqUxgVH33vK" +
                "EfmW1Y/NNSPGNltCcITX9wfukEGLQdrjO699seINdzhvUrCBhRd+fs41S8X04m0ALp/jmMdRV434bF2gOzQk4qRekTyDzPjUdxcI" +
                "O9OGEuDaia1ZL1AlFSzbcHY2d9hEIbOnAKZ5ZBfWNw59Sdw4sNOqV+4p/pvopBSIdAD/Zt9/iLC435ReUqoaLaRVFMR33gr8CgzU" +
                "LvPXcrM+4Uw7wdSGNSNVm75wnyjyyp7cuAEKbFZ3OHsEH+tk0L3AyLdmwt+hK1aMcVausl1Ce0HV0Rf18uh6BUVuWM0BHrf12RI4" +
                "0DgB4qhLkt2oNDAA1vVNse55RgsN")

    // mirror.ftp.example.com.	315360000	IN	RRSIG	CNAME 5 4 315360000 20260901000000 20160901000000 62715 example.com. EjC9SWMbebxzWw67O/dcXuzz8xnDe6JWwH0mthKAG6y4ZIkj877AYqDs8sSFFLrhJRz1TVabn7dm+tESfCqcRoeqiAPGBfMyQIqZjwqzxnIISgBRNP3xcKjCPoEYiDX9bbAiIvzBYDW4qTdpR1HzYFkmNPYJUzW09g86y8q377GvoTXOGuTgr7Rda8e/ol4dxAh609rmq7XESVeatuhoTPt+MoKjmNKfky2W/eqabdHj18AF/aUzD15b+2ZVdrxsCi+WE7O14oHJDsVES1fIgxLZmP+P217P5WHAMWyLmHprCcm6evWZ5hPrLf12YYSQZioYRbN0idePORF7/DkrGg==
    var signedRecord23: String? =
        ("Bm1pcnJvcgNmdHAHZXhhbXBsZQNjb20AAC4AARLMAwABHwAFBQQSzAMAapYVgFfHb4D0+wdleGFtcGxlA2NvbQASML1JYxt5vHNb" +
                "Drs791xe7PPzGcN7olbAfSa2EoAbrLhkiSPzvsBioOzyxIUUuuElHPVNVpuft2b60RJ8KpxGh6qIA8YF8zJAipmPCrPGcghKAFE0" +
                "/fFwqMI+gRiINf1tsCIi/MFgNbipN2lHUfNgWSY09glTNbT2DzrLyrfvsa+hNc4a5OCvtF1rx7+iXh3ECHrT2uartcRJV5q26GhM" +
                "+34ygqOY0p+TLZb96ppt0ePXwAX9pTMPXlv7ZlV2vGwKL5YTs7XigckOxURLV8iDEtmY/4/bXs/lYcAxbIuYemsJybp69ZnmE+st" +
                "/XZhhJBmKhhFs3SJ1485EXv8OSsa")

    // mirror.ftp.example.com.	259200	IN	NSEC	www.example.com. CNAME RRSIG NSEC
    var signedRecord24: String? = "Bm1pcnJvcgNmdHAHZXhhbXBsZQNjb20AAC8AAQAD9IAAGQN3d3cHZXhhbXBsZQNjb20AAAYEAAAAAAM="

    // mirror.ftp.example.com.	259200	IN	RRSIG	NSEC 5 4 259200 20260901000000 20160901000000 62715 example.com. p1/1knEZm6vWTIxb4IwZO77r2UXXTuyWWnRxECWCqsVxscBmIa24SwB0wsA6+VWdmjqahNdfATrQ5a22i6Bwv5O17x10dLEIFbSjCFIn91OlfYrys4xoqGW/yYsPWr1WIcq7A5HprxU9Wy5yiztNg2DPz7v8boN6hHUop3td8kj/6Myh4I5KBAwPCRNaJ02bX+Dm/SsexmWg3bPb7PUEPiXBCrqnVv4NXuzuu++7j9TV8nJ9LDjuVqTKxR5G5isXS/4od+G8QXkmWM3HaDO4zYLnMm2I47V6gGS/EUoPim/2y1e5aBKF0f3JVL7xMQgBQkLZBo5OjH8GFAgSlSP9kg==
    var signedRecord25: String? =
        ("Bm1pcnJvcgNmdHAHZXhhbXBsZQNjb20AAC4AAQAD9IABHwAvBQQAA/SAapYVgFfHb4D0+wdleGFtcGxlA2NvbQCnX/WScRmbq9ZM" +
                "jFvgjBk7vuvZRddO7JZadHEQJYKqxXGxwGYhrbhLAHTCwDr5VZ2aOpqE118BOtDlrbaLoHC/k7XvHXR0sQgVtKMIUif3U6V9ivKz" +
                "jGioZb/Jiw9avVYhyrsDkemvFT1bLnKLO02DYM/Pu/xug3qEdSine13ySP/ozKHgjkoEDA8JE1onTZtf4Ob9Kx7GZaDds9vs9QQ+" +
                "JcEKuqdW/g1e7O6777uP1NXycn0sOO5WpMrFHkbmKxdL/ih34bxBeSZYzcdoM7jNgucybYjjtXqAZL8RSg+Kb/bLV7loEoXR/clU" +
                "vvExCAFCQtkGjk6MfwYUCBKVI/2S")

    // mirror.ftp.example.com.	259200	IN	RRSIG	NSEC 5 4 259200 20260901000000 20160901000000 62715 example.com. bVTQLgj1q1iKDHYO5XJt3IsyDhWefFv5qyX9d11DUPk4m81KAf7ObF2auo+QKKbjHs6PV0Yj9i1djqSpfh2FrYoCPdLH3jKHMJsjKihKwBGpWv5otm1nb/r1jFTYgfDKu4Ru0JRykhNiKWcMaVo3QM14NdOI1l/96zp4KTO/b5qjePoj8KKYceYPXHV2oaqXIZrjptPcjyR1RPg2ahZ1MihG7kXC4k4L+BKuj+7V2EKoLf9eOyE3JqP29FxKNbQC+FDNkd5mZT4lAiGYwz08DGXNSo65G0/FrXr6EQQx0yghnWJRw+UeCu3sAbhq9ErjhvhbFsf5q0+Jm+KTioE/0w==
    var signedRecord26: String? =
        ("Bm1pcnJvcgNmdHAHZXhhbXBsZQNjb20AAC4AAQAD9IABHwAvBQQAA/SAapYVgFfHb4D0+wdleGFtcGxlA2NvbQBtVNAuCPWrWIoM" +
                "dg7lcm3cizIOFZ58W/mrJf13XUNQ+TibzUoB/s5sXZq6j5AopuMezo9XRiP2LV2OpKl+HYWtigI90sfeMocwmyMqKErAEala/mi2" +
                "bWdv+vWMVNiB8Mq7hG7QlHKSE2IpZwxpWjdAzXg104jWX/3rOngpM79vmqN4+iPwophx5g9cdXahqpchmuOm09yPJHVE+DZqFnUy" +
                "KEbuRcLiTgv4Eq6P7tXYQqgt/147ITcmo/b0XEo1tAL4UM2R3mZlPiUCIZjDPTwMZc1KjrkbT8WtevoRBDHTKCGdYlHD5R4K7ewB" +
                "uGr0SuOG+FsWx/mrT4mb4pOKgT/T")

    // www.example.com.	1814400	IN	A	11.22.33.44
    var signedRecord27: String? = "A3d3dwdleGFtcGxlA2NvbQAAAQABABuvgAAECxYhLA=="

    // www.example.com.	1814400	IN	A	55.66.77.88
    var signedRecord28: String? = "A3d3dwdleGFtcGxlA2NvbQAAAQABABuvgAAEN0JNWA=="

    // www.example.com.	1814400	IN	RRSIG	A 5 3 1814400 20260901000000 20160901000000 62715 example.com. re97TYjxeOeiamfnIbIbws6X5sTwCFgmoCP7H7Hs09fRMYzh7seW/yzYvgdFVKLFBNRT3edKS01bJrAc5qSqM74BNPhY9ieQOIAd9kv+zCLv4tI+w9ax0AZ9ThJ841ldzhjLwtsH1J8MDkFBRxwkrnQ0T7VlnJ+dSNrv/0G1ju6i8PV3JtTm293XTgS3Ys9pPd8ntbC4YdbeZuqFgVTmnZudFzM/aNCgnSbXEEC48s2Ad8t5kM30q5NNOi901byhv3PVq8YvumJtxui9PamxjLJ4oc/YSBAnd/6rzWSXs6wtG9A6IAWAFGiLPUxl1r7mgzslFkpq/Ve+Ranaht1agA==
    var signedRecord29: String? =
        ("A3d3dwdleGFtcGxlA2NvbQAALgABABuvgAEfAAEFAwAbr4BqlhWAV8dvgPT7B2V4YW1wbGUDY29tAK3ve02I8Xjnompn5yGyG8LO" +
                "l+bE8AhYJqAj+x+x7NPX0TGM4e7Hlv8s2L4HRVSixQTUU93nSktNWyawHOakqjO+ATT4WPYnkDiAHfZL/swi7+LSPsPWsdAGfU4S" +
                "fONZXc4Yy8LbB9SfDA5BQUccJK50NE+1ZZyfnUja7/9BtY7uovD1dybU5tvd104Et2LPaT3fJ7WwuGHW3mbqhYFU5p2bnRczP2jQ" +
                "oJ0m1xBAuPLNgHfLeZDN9KuTTTovdNW8ob9z1avGL7pibcbovT2psYyyeKHP2EgQJ3f+q81kl7OsLRvQOiAFgBRoiz1MZda+5oM7" +
                "JRZKav1XvkWp2obdWoA=")

    // www.example.com.	1814400	IN	RRSIG	A 5 3 1814400 20260901000000 20160901000000 62715 example.com. sCA0eCHilmA+T1myVClApGUI3j6tOJ5LfL6jOHGJC2QDwAs1WZ6NH+Rnxah9am1REqs2R0wkzcogTwpB8jTEPgud3k6r/UxqrjqVkO3Bn2OzkxOj5O+8n6IKP+Ihb2d3TgGoI/XJyp0hDCDwn4tOmEMm8vJJkRvqRVpIhep3K93mLGrUUTeqLn1zrihE6FJW8F8cK0XhXSgsHtpaP6Z0Di4M9pvSzf7C8dficMR9j2cB0eOofR5dh1ZNO+QLRMcjZzCBFpjd8WdTKPeMwMqyS0JsFb4a2pDuMdwB6gKntpZag28i9+IpIpfxd38nwg3j0DmqiSz1Ae4o65Cn27K5qA==
    var signedRecord30: String? =
        ("A3d3dwdleGFtcGxlA2NvbQAALgABABuvgAEfAAEFAwAbr4BqlhWAV8dvgPT7B2V4YW1wbGUDY29tALAgNHgh4pZgPk9ZslQpQKRl" +
                "CN4+rTieS3y+ozhxiQtkA8ALNVmejR/kZ8WofWptURKrNkdMJM3KIE8KQfI0xD4Lnd5Oq/1Maq46lZDtwZ9js5MTo+TvvJ+iCj/i" +
                "IW9nd04BqCP1ycqdIQwg8J+LTphDJvLySZEb6kVaSIXqdyvd5ixq1FE3qi59c64oROhSVvBfHCtF4V0oLB7aWj+mdA4uDPab0s3+" +
                "wvHX4nDEfY9nAdHjqH0eXYdWTTvkC0THI2cwgRaY3fFnUyj3jMDKsktCbBW+GtqQ7jHcAeoCp7aWWoNvIvfiKSKX8Xd/J8IN49A5" +
                "qoks9QHuKOuQp9uyuag=")

    // www.example.com.	315360000	IN	TXT	"dead0123456789"
    var signedRecord31: String? = "A3d3dwdleGFtcGxlA2NvbQAAEAABEswDAAAPDmRlYWQwMTIzNDU2Nzg5"

    // www.example.com.	315360000	IN	RRSIG	TXT 5 3 315360000 20260901000000 20160901000000 62715 example.com. jRqGBr6fQ91Z5ND1IbWRthEmQ9uxOMkjqpketlUjU6Nztu+Xi3Nz7OYCqAqAyuDXFLiGu9DffDa9kEh3lI9VPvWvNpaOv7mQ/hiHUzdJtBJJw4uXeC9lnKgpaWDghZQN4QyVD4nj7Vtq2AUF9WD7Pi7dVBLBNhzps9/VL8rAkNP2b57U3nFT1kwHmmtKXlKeurOvJNR9Qr1pGoIkrVj6qvUrEhS3iIXrU4e+WkeJ43spdeYMA/Gn/+zsvX68BCytjnwIPlUubgY8ew06eidd99OFqg8oLSSqzmuNB8M5Augkow0ZRCMDrGWeugvcRQqI6dtBeR5vG4lYAROfaprd/w==
    var signedRecord32: String? =
        ("A3d3dwdleGFtcGxlA2NvbQAALgABEswDAAEfABAFAxLMAwBqlhWAV8dvgPT7B2V4YW1wbGUDY29tAI0ahga+n0PdWeTQ9SG1kbYR" +
                "JkPbsTjJI6qZHrZVI1Ojc7bvl4tzc+zmAqgKgMrg1xS4hrvQ33w2vZBId5SPVT71rzaWjr+5kP4Yh1M3SbQSScOLl3gvZZyoKWlg" +
                "4IWUDeEMlQ+J4+1batgFBfVg+z4u3VQSwTYc6bPf1S/KwJDT9m+e1N5xU9ZMB5prSl5SnrqzryTUfUK9aRqCJK1Y+qr1KxIUt4iF" +
                "61OHvlpHieN7KXXmDAPxp//s7L1+vAQsrY58CD5VLm4GPHsNOnonXffThaoPKC0kqs5rjQfDOQLoJKMNGUQjA6xlnroL3EUKiOnb" +
                "QXkebxuJWAETn2qa3f8=")

    // www.example.com.	315360000	IN	RRSIG	TXT 5 3 315360000 20260901000000 20160901000000 62715 example.com. D1bnE1hTUSfI5jJyqiDWanvrqGzKiGF5QSXXIhu1dorNuZmy3Q2wO3SQWO20jYpsfOx08Nw159VsEofLgXUGKtAnfoEMBhCnQ+RHnDhqnFkPz+8k4pIRVq4hCiyN5pTiJQVu25ou3YnHsK3e3aWkpuOiByRChp6ix6VFMoYWe7I4wvzi+XMWbfPHpgKLdBgE7DkpM7l+oIRKX/K0tbKZcdY2yUit1hil38JSftAqZRibLuu3EBl3Aw5ChTPc2czVmZiRyI1pCQMOgw0dDK+jgBCfRTaZeaZTJRq0F8Ja14LKLAletdHMVFMcdNGWp2QdACGp/stHnhX2qKOb/Lwq9A==
    var signedRecord33: String? =
        ("A3d3dwdleGFtcGxlA2NvbQAALgABEswDAAEfABAFAxLMAwBqlhWAV8dvgPT7B2V4YW1wbGUDY29tAA9W5xNYU1EnyOYycqog1mp7" +
                "66hsyohheUEl1yIbtXaKzbmZst0NsDt0kFjttI2KbHzsdPDcNefVbBKHy4F1BirQJ36BDAYQp0PkR5w4apxZD8/vJOKSEVauIQos" +
                "jeaU4iUFbtuaLt2Jx7Ct3t2lpKbjogckQoaeoselRTKGFnuyOML84vlzFm3zx6YCi3QYBOw5KTO5fqCESl/ytLWymXHWNslIrdYY" +
                "pd/CUn7QKmUYmy7rtxAZdwMOQoUz3NnM1ZmYkciNaQkDDoMNHQyvo4AQn0U2mXmmUyUatBfCWteCyiwJXrXRzFRTHHTRlqdkHQAh" +
                "qf7LR54V9qijm/y8KvQ=")

    // www.example.com.	259200	IN	AAAA	2001:db8:0:0:4:3:2:1
    var signedRecord34: String? = "A3d3dwdleGFtcGxlA2NvbQAAHAABAAP0gAAQIAENuAAAAAAABAADAAIAAQ=="

    // www.example.com.	259200	IN	AAAA	2001:db8:0:0:5:6:7:8
    var signedRecord35: String? = "A3d3dwdleGFtcGxlA2NvbQAAHAABAAP0gAAQIAENuAAAAAAABQAGAAcACA=="

    // www.example.com.	259200	IN	RRSIG	AAAA 5 3 259200 20260901000000 20160901000000 62715 example.com. EprMPo8kME1AluL5zllJ4i42wsDOtqM3JzN9HfHlOgFNaFyIoAVQGc/GK2mtm/Zsh1+zPYHHp1rG7roy4+OWFeWZ6ygX7TclxcA9r9TZc4XztDvyGkTC9vzLFjDEnZbe9HQM2y3NCkGWupT57Eqmj5HlNKAtt6cpUgwTR4S7/8rRO6SfTO2xQEIMqElOzFWKqcIA2WN0qGrKA2uOoQJqh1XwKHNcBM3N0ZlvIE8xPZWG16RoqApqeIG4rjJV7hyYgPkB2HTSxInoMIQoVQjFt3JNj0GM2q0BswL6UB3NKw5lwMhMiIib3rbOycL1KxA6uXzL38RvkPo1u6hJUzf+eQ==
    var signedRecord36: String? =
        ("A3d3dwdleGFtcGxlA2NvbQAALgABAAP0gAEfABwFAwAD9IBqlhWAV8dvgPT7B2V4YW1wbGUDY29tABKazD6PJDBNQJbi+c5ZSeIu" +
                "NsLAzrajNyczfR3x5ToBTWhciKAFUBnPxitprZv2bIdfsz2Bx6daxu66MuPjlhXlmesoF+03JcXAPa/U2XOF87Q78hpEwvb8yxYw" +
                "xJ2W3vR0DNstzQpBlrqU+exKpo+R5TSgLbenKVIME0eEu//K0Tukn0ztsUBCDKhJTsxViqnCANljdKhqygNrjqECaodV8ChzXATN" +
                "zdGZbyBPMT2VhtekaKgKaniBuK4yVe4cmID5Adh00sSJ6DCEKFUIxbdyTY9BjNqtAbMC+lAdzSsOZcDITIiIm962zsnC9SsQOrl8" +
                "y9/Eb5D6NbuoSVM3/nk=")

    // www.example.com.	259200	IN	RRSIG	AAAA 5 3 259200 20260901000000 20160901000000 62715 example.com. TkofVFFmlNkSNDrKqZCRcBoSKNVb+CwOzPjR+i+OU+aEgTd9cddTOezzG5knGlfT9kwv2C2T10QYnsMfLsqRuZCEpT9FMA0PF80RTiTMDHxiccufIjgThF02Hn6s6/YllwGoY3Zf5XEYu8JmlZ2IEd3eUh6beZTLpVbjnvYZHkg5/x/sf6BJ2Llpfp8KMct+2K21ct7H+IEBXa13PHVUeW6xS2bT+SbFv4OH9xdLh2P1hpQ8xNRWo4mejQ8BO5DVt2BBcr1Arw2hOIK9bA87Tb+16xfpcjEidu3Ugbcn6t4vId6oSwTXfnI42rUwzbtLskvwmdzXc+sAFK//EB9EgA==
    var signedRecord37: String? =
        ("A3d3dwdleGFtcGxlA2NvbQAALgABAAP0gAEfABwFAwAD9IBqlhWAV8dvgPT7B2V4YW1wbGUDY29tAE5KH1RRZpTZEjQ6yqmQkXAa" +
                "EijVW/gsDsz40fovjlPmhIE3fXHXUzns8xuZJxpX0/ZML9gtk9dEGJ7DHy7KkbmQhKU/RTANDxfNEU4kzAx8YnHLnyI4E4RdNh5+" +
                "rOv2JZcBqGN2X+VxGLvCZpWdiBHd3lIem3mUy6VW4572GR5IOf8f7H+gSdi5aX6fCjHLftittXLex/iBAV2tdzx1VHlusUtm0/km" +
                "xb+Dh/cXS4dj9YaUPMTUVqOJno0PATuQ1bdgQXK9QK8NoTiCvWwPO02/tesX6XIxInbt1IG3J+reLyHeqEsE135yONq1MM27S7JL" +
                "8Jnc13PrABSv/xAfRIA=")

    // www.example.com.	259200	IN	NSEC	mirror.www.example.com. A TXT AAAA RRSIG NSEC
    var signedRecord38: String? = "A3d3dwdleGFtcGxlA2NvbQAALwABAAP0gAAgBm1pcnJvcgN3d3cHZXhhbXBsZQNjb20AAAZAAIAIAAM="

    // www.example.com.	259200	IN	RRSIG	NSEC 5 3 259200 20260901000000 20160901000000 62715 example.com. C2DCU+CiEGatnEM2dsHF0FJdr3aoHnWP1VsfEnqaNDAynnFWRS9b5rRna8qFwMxh7qDvMy+mbNBI4QgCTlPwisExvb2G1Pbz/zKbw62VaxYs6qhwzCQRsUsWiRxXMj6WeX8UozQ93opfGUpiPExZpHFJ7afQqF5E4CUeLPxxAG/HMW3ngm9QjABobTbAq5RAYtZ6mMT28jQl/PgcMuM6fpfQBxi4xpCQPucJGk9rdedTmMe3UyeMxXjoEn3dJadSszvDgillzsm3r92MdpvbCygjbrp4ki3AEGfg14dPQy7xLUV1HmN3/WMppr7YxCubOqe4PEum3z/au53k4XWJHg==
    var signedRecord39: String? =
        ("A3d3dwdleGFtcGxlA2NvbQAALgABAAP0gAEfAC8FAwAD9IBqlhWAV8dvgPT7B2V4YW1wbGUDY29tAAtgwlPgohBmrZxDNnbBxdBS" +
                "Xa92qB51j9VbHxJ6mjQwMp5xVkUvW+a0Z2vKhcDMYe6g7zMvpmzQSOEIAk5T8IrBMb29htT28/8ym8OtlWsWLOqocMwkEbFLFokc" +
                "VzI+lnl/FKM0Pd6KXxlKYjxMWaRxSe2n0KheROAlHiz8cQBvxzFt54JvUIwAaG02wKuUQGLWepjE9vI0Jfz4HDLjOn6X0AcYuMaQ" +
                "kD7nCRpPa3XnU5jHt1MnjMV46BJ93SWnUrM7w4IpZc7Jt6/djHab2wsoI266eJItwBBn4NeHT0Mu8S1FdR5jd/1jKaa+2MQrmzqn" +
                "uDxLpt8/2rud5OF1iR4=")

    // www.example.com.	259200	IN	RRSIG	NSEC 5 3 259200 20260901000000 20160901000000 62715 example.com. mZA6rFOmeT+kIG9QFV5uxFItow488us72IJKEPAvdAfkP55Aa6AsM3VuHJuz+PAEnXHGa8BapLcmp6DVy/CT2F/Cse7TecdPDYBFN/JJptF0iY5awvIidOMcU+K81jcrNJ8ODZIXpCnnG2b1VYnF5BjitCbGLIddhpjI3htMRQDNARRiKEyi83kBR5Z41fjh4/rmXllxj1xhm76g38KFdnEFpTaGCXX8ygjGtNcZGm1GbHVm7t6mNgqztR+JBy+KqrtxPw2GakrTEpiohb+QtDHcv0WsfhFeWA0mqvQXZ+fmsPBezRmd+SLE7CpMb3/l3L/dIOzftCmLFANgqwmcZQ==
    var signedRecord40: String? =
        ("A3d3dwdleGFtcGxlA2NvbQAALgABAAP0gAEfAC8FAwAD9IBqlhWAV8dvgPT7B2V4YW1wbGUDY29tAJmQOqxTpnk/pCBvUBVebsRS" +
                "LaMOPPLrO9iCShDwL3QH5D+eQGugLDN1bhybs/jwBJ1xxmvAWqS3Jqeg1cvwk9hfwrHu03nHTw2ARTfySabRdImOWsLyInTjHFPi" +
                "vNY3KzSfDg2SF6Qp5xtm9VWJxeQY4rQmxiyHXYaYyN4bTEUAzQEUYihMovN5AUeWeNX44eP65l5ZcY9cYZu+oN/ChXZxBaU2hgl1" +
                "/MoIxrTXGRptRmx1Zu7epjYKs7UfiQcviqq7cT8NhmpK0xKYqIW/kLQx3L9FrH4RXlgNJqr0F2fn5rDwXs0ZnfkixOwqTG9/5dy/" +
                "3SDs37QpixQDYKsJnGU=")

    // mirror.www.example.com.	315360000	IN	CNAME	www.example.com.
    var signedRecord41: String? = "Bm1pcnJvcgN3d3cHZXhhbXBsZQNjb20AAAUAARLMAwAAEQN3d3cHZXhhbXBsZQNjb20A"

    // mirror.www.example.com.	315360000	IN	RRSIG	CNAME 5 4 315360000 20260901000000 20160901000000 62715 example.com. DwcFq7JYXV4MMD4Uz+r88qQ30BrsAhR4iOGIGEOi9KjxU3X387u+SEdb8o4bHL9iRQCTuD+Fv5UoB89yksfWbLL9Q+c9ykoL982uDfsf976I9w7oX+GBd/ow4G4oyBWBmoSrIFe/Q1o4+3Ah07qkFbxuE1r26dwt+85vAQaTnOr95nSNVTiUAtfL4gOmFr+A4qf2RU24yE7xdRrByMlW5jcyf34n7qg/yQpt6fvL4Azn+fLTj1lJVwu1FRutcut54aKrLnehhpw+2HsaguRrxig6FhjnHPEgznDgsnjPFdAatLr77sIOnobCJU1agfDzs9qPD0tFA4XxYDTP9qm7vw==
    var signedRecord42: String? =
        ("Bm1pcnJvcgN3d3cHZXhhbXBsZQNjb20AAC4AARLMAwABHwAFBQQSzAMAapYVgFfHb4D0+wdleGFtcGxlA2NvbQAPBwWrslhdXgww" +
                "PhTP6vzypDfQGuwCFHiI4YgYQ6L0qPFTdffzu75IR1vyjhscv2JFAJO4P4W/lSgHz3KSx9Zssv1D5z3KSgv3za4N+x/3voj3Duhf" +
                "4YF3+jDgbijIFYGahKsgV79DWjj7cCHTuqQVvG4TWvbp3C37zm8BBpOc6v3mdI1VOJQC18viA6YWv4Dip/ZFTbjITvF1GsHIyVbm" +
                "NzJ/fifuqD/JCm3p+8vgDOf58tOPWUlXC7UVG61y63nhoqsud6GGnD7YexqC5GvGKDoWGOcc8SDOcOCyeM8V0Bq0uvvuwg6ehsIl" +
                "TVqB8POz2o8PS0UDhfFgNM/2qbu/")

    // mirror.www.example.com.	315360000	IN	RRSIG	CNAME 5 4 315360000 20260901000000 20160901000000 62715 example.com. ScGf3cmXQ/rR8MG1UHuB66bi+IOxvMbRo5f9Q9J9pqtALqnomqj8LyfH6C99QqN+59paJ56yDGA0cd/JQVnRiwHgZawqZMwXXYuOPZY71TqiR4pIWGF3+u5t9wPtfNLw4uv4lOixC0tSBgze1yuFABX+s9o4IKsZvW4HXOUz6e9cLmcTaeqKiA39/SCBqPjyMLqrnxsPiCLOHK3PoryPS48aPE22K8KkAUluZ2pHOp0dnJGJlNXfQF4M4/ogjoW3c6gYpGaNeH0H/dZepXFTkgCyuKH/WB/Ql342jH6iWZRB9w2b+oSuU/ELuhLZa0Um3n4IEqaLO8ooD9X71m4kSg==
    var signedRecord43: String? =
        ("Bm1pcnJvcgN3d3cHZXhhbXBsZQNjb20AAC4AARLMAwABHwAFBQQSzAMAapYVgFfHb4D0+wdleGFtcGxlA2NvbQBJwZ/dyZdD+tHw" +
                "wbVQe4HrpuL4g7G8xtGjl/1D0n2mq0AuqeiaqPwvJ8foL31Co37n2lonnrIMYDRx38lBWdGLAeBlrCpkzBddi449ljvVOqJHikhY" +
                "YXf67m33A+180vDi6/iU6LELS1IGDN7XK4UAFf6z2jggqxm9bgdc5TPp71wuZxNp6oqIDf39IIGo+PIwuqufGw+IIs4crc+ivI9L" +
                "jxo8TbYrwqQBSW5nakc6nR2ckYmU1d9AXgzj+iCOhbdzqBikZo14fQf91l6lcVOSALK4of9YH9CXfjaMfqJZlEH3DZv6hK5T8Qu6" +
                "EtlrRSbefggSpos7yigP1fvWbiRK")

    // mirror.www.example.com.	259200	IN	NSEC	example.com. CNAME RRSIG NSEC
    var signedRecord44: String? = "Bm1pcnJvcgN3d3cHZXhhbXBsZQNjb20AAC8AAQAD9IAAFQdleGFtcGxlA2NvbQAABgQAAAAAAw=="

    // mirror.www.example.com.	259200	IN	RRSIG	NSEC 5 4 259200 20260901000000 20160901000000 62715 example.com. laV5u8JvO3Q8TXq3lUm+Knr4Kzk4Jj9/+BOL8vuUZhoj8WIvtFRhB7kp+uFfITxBg2lgQFQk31D3GbsNkuVMqC5v91CRMWdS26/+DIIY14yzGurmUoGRTtwcifz9hQPqUArCKt3D4KN+47PLBarMxphIBB/AgDL2d8Ut25zpVX544GINCbZf1aWulNzL0P1J1tO78IDKCYzifKUn79CwlinCPW7vLnbDglc5flUeeTW6JDF9tZwE4CeBj9UFFt9O7LzsQRTB0YNHBDJFmd9gF7fpzKU+Kk3iiOh4q5SBKkOIUXbkjDGy25pn1JGA2SpcUNjwDltpMeg/hPg6W6h7qg==
    var signedRecord45: String? =
        ("Bm1pcnJvcgN3d3cHZXhhbXBsZQNjb20AAC4AAQAD9IABHwAvBQQAA/SAapYVgFfHb4D0+wdleGFtcGxlA2NvbQCVpXm7wm87dDxN" +
                "ereVSb4qevgrOTgmP3/4E4vy+5RmGiPxYi+0VGEHuSn64V8hPEGDaWBAVCTfUPcZuw2S5UyoLm/3UJExZ1Lbr/4MghjXjLMa6uZS" +
                "gZFO3ByJ/P2FA+pQCsIq3cPgo37js8sFqszGmEgEH8CAMvZ3xS3bnOlVfnjgYg0Jtl/Vpa6U3MvQ/UnW07vwgMoJjOJ8pSfv0LCW" +
                "KcI9bu8udsOCVzl+VR55NbokMX21nATgJ4GP1QUW307svOxBFMHRg0cEMkWZ32AXt+nMpT4qTeKI6HirlIEqQ4hRduSMMbLbmmfU" +
                "kYDZKlxQ2PAOW2kx6D+E+DpbqHuq")

    // mirror.www.example.com.	259200	IN	RRSIG	NSEC 5 4 259200 20260901000000 20160901000000 62715 example.com. mx4aBYNkoizyDXpkAZ9QsOmKkAz0VDgXXP8MHosC3JeMENZrcE9CuEC1CxT7EOnkZTwxTQgBw1rmNzNZSKj8SqlaFUThyVyM8c0H43X3Yi+EuOwFGYMD8RDy9okA/WFmf+9sNfY1XeEHlJZVQ7irBV1zr3Cl3Hc7WzLmPCFgkAR5D147Z+77B0dnBT+HK39sWAifEYILYM5zsoNMZojjHCfwR4/IFxlZQEGw+e8XuxDT+ugVln40prwMNiQl4ivNT5MlR8vXBCU7frEqDN6+EfbuZlM84aFrRF3hrixUUtLM52Q6kg2oPybNYSLmdFZpv39XEQcnZmRZfXrMNSmh9Q==
    var signedRecord46: String? =
        ("Bm1pcnJvcgN3d3cHZXhhbXBsZQNjb20AAC4AAQAD9IABHwAvBQQAA/SAapYVgFfHb4D0+wdleGFtcGxlA2NvbQCbHhoFg2SiLPIN" +
                "emQBn1Cw6YqQDPRUOBdc/wweiwLcl4wQ1mtwT0K4QLULFPsQ6eRlPDFNCAHDWuY3M1lIqPxKqVoVROHJXIzxzQfjdfdiL4S47AUZ" +
                "gwPxEPL2iQD9YWZ/72w19jVd4QeUllVDuKsFXXOvcKXcdztbMuY8IWCQBHkPXjtn7vsHR2cFP4crf2xYCJ8RggtgznOyg0xmiOMc" +
                "J/BHj8gXGVlAQbD57xe7ENP66BWWfjSmvAw2JCXiK81PkyVHy9cEJTt+sSoM3r4R9u5mUzzhoWtEXeGuLFRS0sznZDqSDag/Js1h" +
                "IuZ0Vmm/f1cRBydmZFl9esw1KaH1")

    // example.com.		1234000	IN	DS	17515 5 2 26EA264309D0568C5E7EC7DF412A5CB03CE2EACAAEE366B2F66D23611C0D7BF5
    var dsRecord0: String? = "B2V4YW1wbGUDY29tAAArAAEAEtRQACREawUCJuomQwnQVoxefsffQSpcsDzi6squ42ay9m0jYRwNe/U="

    // example.com.		1234000	IN	DS	17515 5 2 26EA264309D0568C5E7EC7DF412A5CB03CE2EACAAEE366B2F66D23611C0D7BF5
    var dsRecord1: String? = "B2V4YW1wbGUDY29tAAArAAEAEtRQACREawUCJuomQwnQVoxefsffQSpcsDzi6squ42ay9m0jYRwNe/U="

    // example.com.		31556952	IN	DNSKEY	256 3 5 MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAymLTAjeDfcIYUKyGqKhUrl/khgBJA9TNqrzZOfMmNFarbONxDnsd6WoHnqi5xGrNBV6ZGIGwc4tebG/XWBOVvI7Pb10ZHjm4muKnzA9Qt+TOwpukN/phOzDwFZx+QHHu18jePgmFstoSUJzb4baPSoLorCYbRKdIAHhSEALfL5LE8ByP/MwWCO6jD0wEZlzGsnow5wxnuVWhBt8FMpRN9FgrJ3YkfTxKz8IZpSx2yjf9IIa/lGvKxcUoAyrdWam14l3fBTI6tfx2nWv56L846wXjqtcZvQeO0ewFdwNw2kWTT01kWeG6lXa1yo6CARkvVaF9zcrVNxWUm7CIvKibwQIDAQAB
    // keytag 62715
    var zoneDnsKeyRecord: String? =
        ("ZXhhbXBsZS5jb20uCQkzMTU1Njk1MglJTglETlNLRVkJMjU2IDMgNSBNSUlCSWpBTkJna3Foa2lH\nOXcwQkFRRUZBQU9DQVE4QU" +
                "1JSUJDZ0tDQVFFQXltTFRBamVEZmNJWVVLeUdxS2hVcmwva2hnQkpB\nOVROcXJ6Wk9mTW1ORmFyYk9OeERuc2Q2V29IbnFpNXhH" +
                "ck5CVjZaR0lHd2M0dGViRy9YV0JPVnZJ\nN1BiMTBaSGptNG11S256QTlRdCtUT3dwdWtOL3BoT3pEd0ZaeCtRSEh1MThqZVBnbU" +
                "ZzdG9TVUp6\nYjRiYVBTb0xvckNZYlJLZElBSGhTRUFMZkw1TEU4QnlQL013V0NPNmpEMHdFWmx6R3Nub3c1d3hu\ndVZXaEJ0OE" +
                "ZNcFJOOUZnckozWWtmVHhLejhJWnBTeDJ5amY5SUlhL2xHdkt4Y1VvQXlyZFdhbTE0\nbDNmQlRJNnRmeDJuV3Y1Nkw4NDZ3WGpx" +
                "dGNadlFlTzBld0Zkd053MmtXVFQwMWtXZUc2bFhhMXlv\nNkNBUmt2VmFGOXpjclZOeFdVbTdDSXZLaWJ3UUlEQVFBQg==")

    // example.com.		315569520	IN	DNSKEY	257 3 5 MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAly/1SbKJpzYwOJF2Xie7W6eLyQ/W1Ar8hKss7ZbIkcg23bt8QQOFVLPlYG9luYzAULZgTWa4gFlrBkEzO410oy8VFZgB5x11/LioWGJmy9h+H6R1Fy0QFP3eFGKb9tLuAJGMaSRTcbRADJQYiDJ6uuWobTg2fNxlb7B1lz7wOVk/yTV795k+vb+lJx8xZu9vNyIkUy2/LF4J0oXKCPUEee0hpBglEeFcnMSHjO+LtY5Y6E8+fp3d38+Tikmy/2Xu0R35MmCWXuwqYMO+1p7spNzsuUFkhTWt0yJKc8pC91V6e3gsD6iwMy3Q0EEEQ7q1z+M9vLIYtmC27mHmdDh1DQIDAQAB
    // keytag 62715
    var keyDnsKeyRecord: String? =
        ("ZXhhbXBsZS5jb20uCQkzMTU1Njk1MjAJSU4JRE5TS0VZCTI1NyAzIDUgTUlJQklqQU5CZ2txaGtp\nRzl3MEJBUUVGQUFPQ0FROE" +
                "FNSUlCQ2dLQ0FRRUFseS8xU2JLSnB6WXdPSkYyWGllN1c2ZUx5US9X\nMUFyOGhLc3M3WmJJa2NnMjNidDhRUU9GVkxQbFlHOWx1" +
                "WXpBVUxaZ1RXYTRnRmxyQmtFek80MTBv\neThWRlpnQjV4MTEvTGlvV0dKbXk5aCtINlIxRnkwUUZQM2VGR0tiOXRMdUFKR01hU1" +
                "JUY2JSQURK\nUVlpREo2dXVXb2JUZzJmTnhsYjdCMWx6N3dPVmsveVRWNzk1ayt2YitsSng4eFp1OXZOeUlrVXky\nL0xGNEowb1" +
                "hLQ1BVRWVlMGhwQmdsRWVGY25NU0hqTytMdFk1WTZFOCtmcDNkMzgrVGlrbXkvMlh1\nMFIzNU1tQ1dYdXdxWU1PKzFwN3NwTnpz" +
                "dVVGa2hUV3QweUpLYzhwQzkxVjZlM2dzRDZpd015M1Ew\nRUVFUTdxMXorTTl2TElZdG1DMjdtSG1kRGgxRFFJREFRQUI=")
    var signedList: MutableList<Record?>? = null
    var postZoneList: MutableList<Record?>? = null
    var dsRecordList: MutableList<Record?>? = ArrayList()
    fun getStringsNamedLike(name: String?, clazz: Class<*>?): MutableList<String?>? {
        return Arrays.asList(*clazz.getDeclaredFields()).stream()
            .filter(Predicate({ field: Field? -> field.getName().contains(name) }))
            .map(Function<Field?, String?>({ field: Field? ->
                try {
                    return@map field.get(null).toString()
                } catch (e: Exception) {
                    println("Failed getting static field " + name + " for class " + clazz)
                    e.printStackTrace()
                }
                null
            }))
            .collect(Collectors.toList())
    }

    fun toRecord(record: String?): Record? {
        try {
            return Record.fromWire(Base64.getDecoder().decode(record.toByteArray()), Section.ANSWER)
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return null
    }

    @Throws(Exception::class)
    fun recreateData() {
        var encodedRecords: MutableList<String?>? = getStringsNamedLike("signedRecord", SigningData::class.java)
        signedList = encodedRecords.stream().map(Function({ obj: String? -> toRecord() })).collect(Collectors.toList())
        encodedRecords = getStringsNamedLike("postZoneRecord", SigningData::class.java)
        postZoneList =
            encodedRecords.stream().map(Function({ obj: String? -> toRecord() })).collect(Collectors.toList())
        dsRecordList.clear()
        dsRecordList.add(toRecord(dsRecord0))
        dsRecordList.add(toRecord(dsRecord1))
    }
}