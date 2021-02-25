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
package com.comcast.cdn.traffic_control.traffic_router.core.dns.protocol

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
import java.util.concurrent.ExecutorService
import java.util.concurrent.LinkedBlockingQueue
import com.comcast.cdn.traffic_control.traffic_router.core.dns.protocol.TCP
import java.io.ByteArrayInputStream
import java.util.concurrent.BlockingQueue
import java.lang.Runnable
import com.comcast.cdn.traffic_control.traffic_router.core.dns.protocol.TCP.TCPSocketHandler
import java.lang.RuntimeException
import org.powermock.api.mockito.PowerMockito
import com.comcast.cdn.traffic_control.traffic_router.core.dns.protocol.UDP
import java.util.concurrent.atomic.AtomicInteger
import com.comcast.cdn.traffic_control.traffic_router.core.dns.protocol.UDP.UDPPacketHandler
import com.comcast.cdn.traffic_control.traffic_router.core.dns.protocol.AbstractProtocolTest.FakeAbstractProtocol
import java.lang.System
import com.comcast.cdn.traffic_control.traffic_router.core.dns.protocol.AbstractProtocolTest
import com.comcast.cdn.traffic_control.traffic_router.core.router.TrafficRouterManager
import com.comcast.cdn.traffic_control.traffic_router.core.router.TrafficRouter
import com.comcast.cdn.traffic_control.traffic_router.core.edge.CacheRegister
import com.comcast.cdn.traffic_control.traffic_router.core.util.JsonUtils
import java.util.HashSet
import com.comcast.cdn.traffic_control.traffic_router.core.util.IntegrationTest
import java.util.HashMap
import com.google.common.net.InetAddresses
import com.comcast.cdn.traffic_control.traffic_router.core.edge.CacheLocation
import com.comcast.cdn.traffic_control.traffic_router.core.edge.Node.IPVersions
import com.google.common.cache.CacheStats
import java.nio.file.Paths
import com.comcast.cdn.traffic_control.traffic_router.core.TestBase
import com.comcast.cdn.traffic_control.traffic_router.core.util.TrafficOpsUtils
import com.comcast.cdn.traffic_control.traffic_router.core.router.StatTracker
import com.comcast.cdn.traffic_control.traffic_router.core.router.StatTracker.Track.ResultType
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
import com.comcast.cdn.traffic_control.traffic_router.core.dns.*
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
import org.junit.*
import org.mockito.Matchers
import org.mockito.Mockito
import org.mockito.invocation.InvocationOnMock
import org.xbill.DNS.*
import java.net.*
import java.util.concurrent.ThreadPoolExecutor

@RunWith(PowerMockRunner::class)
@PrepareForTest(AbstractProtocol::class, Message::class)
class UDPTest {
    private var datagramSocket: DatagramSocket? = null
    private var executorService: ThreadPoolExecutor? = null
    private var cancelService: ExecutorService? = null
    private var queue: LinkedBlockingQueue<*>? = null
    private var nameServer: NameServer? = null
    private var udp: UDP? = null
    @Before
    @Throws(Exception::class)
    fun setUp() {
        datagramSocket = Mockito.mock(DatagramSocket::class.java)
        executorService = Mockito.mock(ThreadPoolExecutor::class.java)
        cancelService = Mockito.mock(ExecutorService::class.java)
        queue = Mockito.mock(LinkedBlockingQueue::class.java)
        nameServer = Mockito.mock(NameServer::class.java)
        udp = UDP()
        udp!!.datagramSocket = datagramSocket
        udp!!.executorService = executorService
        udp!!.cancelService = cancelService
        udp!!.nameServer = nameServer
        Mockito.`when`(executorService.getQueue()).thenReturn(queue)
        Mockito.`when`(queue.size).thenReturn(0)
    }

    @Test
    @Throws(Exception::class)
    fun testGetMaxResponseLengthNoOPTQuery() {
        val name = Name.fromString("www.foo.com.")
        val question = Record.newRecord(name, Type.A, DClass.IN)
        val request = Message.newQuery(question)
        Assert.assertEquals(512, udp!!.getMaxResponseLength(request).toLong())
    }

    @Test
    fun testGetMaxResponseLengthNullQuery() {
        Assert.assertEquals(512, udp!!.getMaxResponseLength(null).toLong())
    }

    @Test
    @Throws(Exception::class)
    fun testGetMaxResponseLengthWithOPTQuery() {
        val size = 1280
        val name = Name.fromString("www.foo.com.")
        val question = Record.newRecord(name, Type.A, DClass.IN)
        val options = OPTRecord(size, 0, 0)
        val request = Message.newQuery(question)
        request.addRecord(options, Section.ADDITIONAL)
        Assert.assertEquals(size.toLong(), udp!!.getMaxResponseLength(request).toLong())
    }

    @Test
    @Throws(Exception::class)
    fun testSubmit() {
        val r = Mockito.mock(
            SocketHandler::class.java
        )
        udp!!.submit(r)
        Mockito.verify(executorService).submit(r)
    }

    @Test
    @Throws(Exception::class)
    fun testUDPPacketHandler() {
        val client = InetAddress.getLocalHost()
        val port = 11111
        val name = Name.fromString("www.foo.bar.")
        val question = Record.newRecord(name, Type.A, DClass.IN)
        val request = Message.newQuery(question)
        val wireRequest = request.toWire()
        val aRecord = Record.newRecord(name, Type.A, DClass.IN, 3600)
        val response = Message.newQuery(question)
        response.header.setFlag(Flags.QR.toInt())
        response.addRecord(aRecord, Section.ANSWER)
        val wireResponse = response.toWire()
        val packet = DatagramPacket(wireRequest, wireRequest.size, client, port)
        Mockito.`when`(
            nameServer!!.query(
                Matchers.any(
                    Message::class.java
                ), Matchers.eq(client), Matchers.any(
                    DNSAccessRecord.Builder::class.java
                )
            )
        ).thenReturn(response)
        val count = AtomicInteger(0)
        Mockito.doAnswer { invocation: InvocationOnMock ->
            val datagramPacket = invocation.arguments[0] as DatagramPacket
            MatcherAssert.assertThat(datagramPacket.data, org.hamcrest.Matchers.equalTo(wireResponse))
            count.incrementAndGet()
            null
        }.`when`(datagramSocket).send(Matchers.any(DatagramPacket::class.java))
        val handler = udp!!.UDPPacketHandler(packet)
        handler.run()
        MatcherAssert.assertThat(count.get(), org.hamcrest.Matchers.equalTo(1))
    }

    @Test
    @Throws(Exception::class)
    fun testUDPPacketHandlerBadMessage() {
        val client = InetAddress.getLocalHost()
        val port = 11111
        val wireRequest = ByteArray(0)
        val packet = DatagramPacket(wireRequest, wireRequest.size, client, port)
        val handler = udp!!.UDPPacketHandler(packet)
        handler.run()
    }

    @Test
    @Throws(Exception::class)
    fun testUDPPacketHandlerQueryFail() {
        val client = InetAddress.getLocalHost()
        val port = 11111
        val name = Name.fromString("www.foo.bar.")
        val question = Record.newRecord(name, Type.A, DClass.IN)
        val request = Message.newQuery(question)
        val wireRequest = request.toWire()
        val response = Message()
        response.header = request.header
        for (i in 0..3) {
            response.removeAllRecords(i)
        }
        response.addRecord(question, Section.QUESTION)
        response.header.rcode = Rcode.SERVFAIL
        val wireResponse = response.toWire()
        val packet = DatagramPacket(wireRequest, wireRequest.size, client, port)
        val count = AtomicInteger(0)
        Mockito.`when`(
            nameServer!!.query(
                Matchers.any(
                    Message::class.java
                ), Matchers.eq(client), Matchers.any(
                    DNSAccessRecord.Builder::class.java
                )
            )
        ).thenThrow(RuntimeException("Boom! UDP Query"))
        Mockito.doAnswer { invocation: InvocationOnMock ->
            val datagramPacket = invocation.arguments[0] as DatagramPacket
            MatcherAssert.assertThat(datagramPacket.data, org.hamcrest.Matchers.equalTo(wireResponse))
            count.incrementAndGet()
            null
        }.`when`(datagramSocket).send(Matchers.any(DatagramPacket::class.java))
        val handler = udp!!.UDPPacketHandler(packet)
        handler.run()
        MatcherAssert.assertThat(count.get(), org.hamcrest.Matchers.equalTo(1))
    }
}