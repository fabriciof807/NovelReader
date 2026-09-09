package com.novelreader.util

import okhttp3.Dns
import java.net.InetAddress
import java.net.UnknownHostException

class PublicOnlyDns(
    private val delegate: Dns = Dns.SYSTEM
) : Dns {

    override fun lookup(hostname: String): List<InetAddress> {
        val addresses = delegate.lookup(hostname)
        val allowed = addresses.filter { isPublicAddress(it) }
        if (allowed.isEmpty()) {
            throw UnknownHostException("Blocked non-public address for $hostname")
        }
        return allowed
    }

    companion object {
        fun isPublicAddress(address: InetAddress): Boolean {
            if (address.isLoopbackAddress ||
                address.isSiteLocalAddress ||
                address.isLinkLocalAddress ||
                address.isAnyLocalAddress ||
                address.isMulticastAddress
            ) {
                return false
            }
            val bytes = address.address
            if (bytes.size == 4) {
                val first = bytes[0].toInt() and 0xff
                val second = bytes[1].toInt() and 0xff
                if (first == 0) return false
                if (first == 100 && second in 64..127) return false
                if (first >= 224) return false
            } else if (bytes.size == 16) {
                val first = bytes[0].toInt() and 0xff
                if (first and 0xfe == 0xfc) return false
            }
            return true
        }
    }
}
