package com.novelreader.util

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class RemoteHostGuardTest {

    @Test
    fun `rejects loopback and unspecified hosts`() {
        assertThat(RemoteHostGuard.isAllowed("localhost")).isFalse()
        assertThat(RemoteHostGuard.isAllowed("127.0.0.1")).isFalse()
        assertThat(RemoteHostGuard.isAllowed("0.0.0.0")).isFalse()
        assertThat(RemoteHostGuard.isAllowed("::1")).isFalse()
        assertThat(RemoteHostGuard.isAllowed("[::1]")).isFalse()
        assertThat(RemoteHostGuard.isAllowed("2130706433")).isFalse()
    }

    @Test
    fun `rejects private and link-local ranges`() {
        assertThat(RemoteHostGuard.isAllowed("10.0.0.1")).isFalse()
        assertThat(RemoteHostGuard.isAllowed("172.16.0.1")).isFalse()
        assertThat(RemoteHostGuard.isAllowed("172.31.255.255")).isFalse()
        assertThat(RemoteHostGuard.isAllowed("192.168.1.1")).isFalse()
        assertThat(RemoteHostGuard.isAllowed("169.254.1.1")).isFalse()
        assertThat(RemoteHostGuard.isAllowed("100.64.0.1")).isFalse()
        assertThat(RemoteHostGuard.isAllowed("224.0.0.1")).isFalse()
        assertThat(RemoteHostGuard.isAllowed("fd00::1")).isFalse()
        assertThat(RemoteHostGuard.isAllowed("fe80::1")).isFalse()
    }

    @Test
    fun `rejects internal host suffixes`() {
        assertThat(RemoteHostGuard.isAllowed("printer.local")).isFalse()
        assertThat(RemoteHostGuard.isAllowed("api.internal")).isFalse()
        assertThat(RemoteHostGuard.isAllowed("router.home.arpa")).isFalse()
    }

    @Test
    fun `accepts public hosts and addresses`() {
        assertThat(RemoteHostGuard.isAllowed("example.com")).isTrue()
        assertThat(RemoteHostGuard.isAllowed("freewebnovel.com")).isTrue()
        assertThat(RemoteHostGuard.isAllowed("m.readnovelfull.com")).isTrue()
        assertThat(RemoteHostGuard.isAllowed("8.8.8.8")).isTrue()
        assertThat(RemoteHostGuard.isAllowed("1.1.1.1")).isTrue()
        assertThat(RemoteHostGuard.isAllowed("172.32.0.1")).isTrue()
        assertThat(RemoteHostGuard.isAllowed("2001:4860:4860::8888")).isTrue()
    }

    @Test
    fun `rejects blank hosts`() {
        assertThat(RemoteHostGuard.isAllowed("")).isFalse()
        assertThat(RemoteHostGuard.isAllowed("   ")).isFalse()
    }
}
