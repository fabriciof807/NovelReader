package com.novelreader.util

import com.google.common.truth.Truth.assertThat
import okhttp3.Dns
import org.junit.Test
import java.net.InetAddress
import java.net.UnknownHostException
import org.junit.Assert.assertThrows

class PublicOnlyDnsTest {

    private val publicAddress = InetAddress.getByName("93.184.216.34")
    private val loopback = InetAddress.getByName("127.0.0.1")
    private val siteLocal = InetAddress.getByName("192.168.1.10")
    private val linkLocal = InetAddress.getByName("169.254.1.1")
    private val cgnat = InetAddress.getByName("100.64.0.1")
    private val ipv6Loopback = InetAddress.getByName("::1")
    private val ipv6UniqueLocal = InetAddress.getByName("fd00::1")

    private fun dnsReturning(vararg addresses: InetAddress) = object : Dns {
        override fun lookup(hostname: String): List<InetAddress> = addresses.toList()
    }

    @Test
    fun `returns only public answers`() {
        val dns = PublicOnlyDns(dnsReturning(publicAddress, siteLocal, loopback))

        assertThat(dns.lookup("example.com")).containsExactly(publicAddress)
    }

    @Test
    fun `throws when every answer is private`() {
        val dns = PublicOnlyDns(dnsReturning(siteLocal, linkLocal, ipv6Loopback))

        assertThrows(UnknownHostException::class.java) { dns.lookup("rebind.example") }
    }

    @Test
    fun `classifies public and private addresses`() {
        assertThat(PublicOnlyDns.isPublicAddress(publicAddress)).isTrue()
        assertThat(PublicOnlyDns.isPublicAddress(InetAddress.getByName("8.8.8.8"))).isTrue()
        assertThat(PublicOnlyDns.isPublicAddress(loopback)).isFalse()
        assertThat(PublicOnlyDns.isPublicAddress(siteLocal)).isFalse()
        assertThat(PublicOnlyDns.isPublicAddress(linkLocal)).isFalse()
        assertThat(PublicOnlyDns.isPublicAddress(cgnat)).isFalse()
        assertThat(PublicOnlyDns.isPublicAddress(ipv6Loopback)).isFalse()
        assertThat(PublicOnlyDns.isPublicAddress(ipv6UniqueLocal)).isFalse()
        assertThat(PublicOnlyDns.isPublicAddress(InetAddress.getByName("0.0.0.0"))).isFalse()
        assertThat(PublicOnlyDns.isPublicAddress(InetAddress.getByName("224.0.0.1"))).isFalse()
    }
}
