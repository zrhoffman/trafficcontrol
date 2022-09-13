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
package org.apache.traffic_control.traffic_router.core.dns

import org.hamcrest.MatcherAssert
import org.hamcrest.Matchers
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatcher
import org.mockito.ArgumentMatchers
import org.mockito.Mockito
import org.powermock.api.mockito.PowerMockito
import org.powermock.core.classloader.annotations.PowerMockIgnore
import org.powermock.core.classloader.annotations.PrepareForTest
import org.powermock.modules.junit4.PowerMockRunner
import org.xbill.DNS.*
import java.net.InetAddress
import java.security.PrivateKey
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentMap

@RunWith(PowerMockRunner::class)
@PrepareForTest(ZoneSignerImpl::class)
@PowerMockIgnore("javax.management.*")
class ZoneSignerImplTest {
    internal class IsRRsetTypeA : ArgumentMatcher<RRset?> {
        override fun matches(rRset: RRset?): Boolean {
            return rRset.getType() == Type.A
        }
    }

    internal class IsRRsetTypeNSEC : ArgumentMatcher<RRset?> {
        override fun matches(rRset: RRset?): Boolean {
            return rRset.getType() == Type.NSEC
        }
    }

    @Test
    @Throws(Exception::class)
    fun signZoneWithRRSIGCacheTest() {
        val zoneSigner = PowerMockito.spy(ZoneSignerImpl())
        val records: MutableList<Record?> = ArrayList()
        val ARecord1: Record = ARecord(Name("foo.example.com."), DClass.IN, 60, InetAddress.getByName("1.2.3.4"))
        val ARecord2: Record = ARecord(Name("foo.example.com."), DClass.IN, 60, InetAddress.getByName("1.2.3.5"))
        val ARecord3: Record = ARecord(Name("foo.example.com."), DClass.IN, 60, InetAddress.getByName("1.2.3.6"))
        val ARecord4: Record = ARecord(Name("foo.example.com."), DClass.IN, 60, InetAddress.getByName("1.2.3.7"))
        val zskPair: DnsSecKeyPair? = Mockito.mock(DnsSecKeyPairImpl::class.java)
        val zskDnskey = Mockito.mock(DNSKEYRecord::class.java)
        Mockito.`when`(zskPair.getDNSKEYRecord()).thenReturn(zskDnskey)
        val zskKey = Mockito.mock(PrivateKey::class.java)
        Mockito.`when`(zskPair.getPrivate()).thenReturn(zskKey)
        Mockito.`when`(zskKey.encoded).thenReturn(byteArrayOf(1))
        Mockito.`when`(zskDnskey.algorithm).thenReturn(1)
        val kskPairs: MutableList<DnsSecKeyPair?> = ArrayList()
        val zskPairs: MutableList<DnsSecKeyPair?> = listOf(zskPair)
        val inception = Date()
        val expire = Date.from(inception.toInstant().plusSeconds(100000))
        val aRRSigRecord = RRSIGRecord(Name("foo.example.com."), DClass.IN, 60, Type.A, 1, 60, inception, expire, 1, Name("example.com."), byteArrayOf(1))
        val nsecRRSigRecord = RRSIGRecord(Name("foo.example.com."), DClass.IN, 60, Type.NSEC, 1, 60, inception, expire, 1, Name("example.com."), byteArrayOf(2))
        PowerMockito.doReturn(aRRSigRecord).`when`(zoneSigner, "sign", ArgumentMatchers.argThat(IsRRsetTypeA()), ArgumentMatchers.any(DNSKEYRecord::class.java), ArgumentMatchers.any(PrivateKey::class.java), ArgumentMatchers.eq(inception), ArgumentMatchers.eq(expire))
        PowerMockito.doReturn(nsecRRSigRecord).`when`(zoneSigner, "sign", ArgumentMatchers.argThat(IsRRsetTypeNSEC()), ArgumentMatchers.any(DNSKEYRecord::class.java), ArgumentMatchers.any(PrivateKey::class.java), ArgumentMatchers.eq(inception), ArgumentMatchers.eq(expire))
        val newInception = Date.from(inception.toInstant().plusSeconds(100))
        val newExpire = Date.from(newInception.toInstant().plusSeconds(100000))
        val newARRSigRecord = RRSIGRecord(Name("foo.example.com."), DClass.IN, 60, Type.A, 1, 60, newInception, newExpire, 1, Name("example.com."), byteArrayOf(3))
        val newNSECRRSigRecord = RRSIGRecord(Name("foo.example.com."), DClass.IN, 60, Type.NSEC, 1, 60, newInception, newExpire, 1, Name("example.com."), byteArrayOf(4))
        PowerMockito.doReturn(newARRSigRecord).`when`(zoneSigner, "sign", ArgumentMatchers.argThat(IsRRsetTypeA()), ArgumentMatchers.any(DNSKEYRecord::class.java), ArgumentMatchers.any(PrivateKey::class.java), ArgumentMatchers.eq(newInception), ArgumentMatchers.eq(newExpire))
        PowerMockito.doReturn(newNSECRRSigRecord).`when`(zoneSigner, "sign", ArgumentMatchers.argThat(IsRRsetTypeNSEC()), ArgumentMatchers.any(DNSKEYRecord::class.java), ArgumentMatchers.any(PrivateKey::class.java), ArgumentMatchers.eq(newInception), ArgumentMatchers.eq(newExpire))
        val expiresSoonInception = Date.from(inception.toInstant().minusSeconds(100))
        val expiresSoonExpire = Date.from(inception.toInstant().plusSeconds(50))
        val expiresSoonARRSigRecord = RRSIGRecord(Name("foo.example.com."), DClass.IN, 60, Type.A, 1, 60, expiresSoonInception, expiresSoonExpire, 1, Name("example.com."), byteArrayOf(5))
        val expiresSoonNSECRRSigRecord = RRSIGRecord(Name("foo.example.com."), DClass.IN, 60, Type.NSEC, 1, 60, expiresSoonInception, expiresSoonExpire, 1, Name("example.com."), byteArrayOf(6))
        PowerMockito.doReturn(expiresSoonARRSigRecord).`when`(zoneSigner, "sign", ArgumentMatchers.argThat(IsRRsetTypeA()), ArgumentMatchers.any(DNSKEYRecord::class.java), ArgumentMatchers.any(PrivateKey::class.java), ArgumentMatchers.eq(expiresSoonInception), ArgumentMatchers.eq(expiresSoonExpire))
        PowerMockito.doReturn(expiresSoonNSECRRSigRecord).`when`(zoneSigner, "sign", ArgumentMatchers.argThat(IsRRsetTypeNSEC()), ArgumentMatchers.any(DNSKEYRecord::class.java), ArgumentMatchers.any(PrivateKey::class.java), ArgumentMatchers.eq(expiresSoonInception), ArgumentMatchers.eq(expiresSoonExpire))
        val RRSIGCache: ConcurrentMap<RRSIGCacheKey?, ConcurrentMap<RRsetKey?, RRSIGRecord?>?> = ConcurrentHashMap()
        records.add(ARecord1)
        records.add(ARecord2)
        var signedRecords = zoneSigner.signZone(records, kskPairs, zskPairs, inception, expire, RRSIGCache)
        var ret = signedRecords.stream().filter { r: Record? -> r is RRSIGRecord && (r as RRSIGRecord?).getTypeCovered() == Type.A }.findFirst().orElse(null) as RRSIGRecord
        MatcherAssert.assertThat(ret, Matchers.notNullValue())
        MatcherAssert.assertThat(ret, Matchers.equalTo(aRRSigRecord))

        // re-signing the same RRset with new timestamps should reuse the cached RRSIG record
        records.clear()
        records.add(ARecord1)
        records.add(ARecord2)
        signedRecords = zoneSigner.signZone(records, kskPairs, zskPairs, newInception, newExpire, RRSIGCache)
        ret = signedRecords.stream().filter { r: Record? -> r is RRSIGRecord && (r as RRSIGRecord?).getTypeCovered() == Type.A }.findFirst().orElse(null) as RRSIGRecord
        MatcherAssert.assertThat(ret, Matchers.notNullValue())
        MatcherAssert.assertThat(ret, Matchers.equalTo(aRRSigRecord))

        // changed RRset should be re-signed
        records.clear()
        records.add(ARecord1)
        records.add(ARecord2)
        records.add(ARecord3)
        records.add(ARecord4)
        signedRecords = zoneSigner.signZone(records, kskPairs, zskPairs, newInception, newExpire, RRSIGCache)
        ret = signedRecords.stream().filter { r: Record? -> r is RRSIGRecord && (r as RRSIGRecord?).getTypeCovered() == Type.A }.findFirst().orElse(null) as RRSIGRecord
        MatcherAssert.assertThat(ret, Matchers.notNullValue())
        MatcherAssert.assertThat(ret, Matchers.equalTo(newARRSigRecord))

        // re-signing 1st RRset again should reuse the cached RRSIG record
        records.clear()
        records.add(ARecord1)
        records.add(ARecord2)
        signedRecords = zoneSigner.signZone(records, kskPairs, zskPairs, newInception, newExpire, RRSIGCache)
        ret = signedRecords.stream().filter { r: Record? -> r is RRSIGRecord && (r as RRSIGRecord?).getTypeCovered() == Type.A }.findFirst().orElse(null) as RRSIGRecord
        MatcherAssert.assertThat(ret, Matchers.notNullValue())
        MatcherAssert.assertThat(ret, Matchers.equalTo(aRRSigRecord))

        // re-signing RRset that has a cached RRSIG record that is close to expiring should be re-signed
        records.clear()
        records.add(ARecord3)
        records.add(ARecord4)
        signedRecords = zoneSigner.signZone(records, kskPairs, zskPairs, expiresSoonInception, expiresSoonExpire, RRSIGCache)
        ret = signedRecords.stream().filter { r: Record? -> r is RRSIGRecord && (r as RRSIGRecord?).getTypeCovered() == Type.A }.findFirst().orElse(null) as RRSIGRecord
        MatcherAssert.assertThat(ret, Matchers.notNullValue())
        MatcherAssert.assertThat(ret, Matchers.equalTo(expiresSoonARRSigRecord))
        records.clear()
        records.add(ARecord3)
        records.add(ARecord4)
        signedRecords = zoneSigner.signZone(records, kskPairs, zskPairs, newInception, newExpire, RRSIGCache)
        ret = signedRecords.stream().filter { r: Record? -> r is RRSIGRecord && (r as RRSIGRecord?).getTypeCovered() == Type.A }.findFirst().orElse(null) as RRSIGRecord
        MatcherAssert.assertThat(ret, Matchers.notNullValue())
        MatcherAssert.assertThat(ret, Matchers.equalTo(newARRSigRecord))
    }
}