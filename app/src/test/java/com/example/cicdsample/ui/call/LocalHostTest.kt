package com.example.cicdsample.ui.call

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 내 주소를 고르는 규칙 테스트.
 *
 * 열거는 [NetworkLocalHostProvider] 가 하고 고르는 규칙만 순수 함수로 떼어 두었으므로,
 * 기기도 Wi-Fi 도 없이 "여러 주소가 잡혔을 때 무엇을 건네줄 것인가"를 확인할 수 있다.
 */
class LocalHostTest {

    @Test
    fun `후보가 없으면 null 이다`() {
        assertNull(pickLocalHost(emptyList()))
    }

    @Test
    fun `가정용 Wi-Fi 주소를 가장 먼저 고른다`() {
        val picked = pickLocalHost(listOf("10.5.0.7", "192.168.0.5"))

        assertEquals("192.168.0.5", picked)
    }

    @Test
    fun `사설 B 클래스가 10 대역보다 앞선다`() {
        val picked = pickLocalHost(listOf("10.5.0.7", "172.20.1.4"))

        assertEquals("172.20.1.4", picked)
    }

    @Test
    fun `172 대역이라도 사설 범위를 벗어나면 뒤로 밀린다`() {
        // 172.32 는 공인 주소다. 사설인 10 대역이 더 닿을 만하다.
        val picked = pickLocalHost(listOf("172.32.1.4", "10.5.0.7"))

        assertEquals("10.5.0.7", picked)
    }

    @Test
    fun `링크 로컬 주소는 아예 고르지 않는다`() {
        // DHCP 를 못 받았을 때 붙는 주소라 상대가 닿지 못한다.
        assertNull(pickLocalHost(listOf("169.254.10.20")))
        assertEquals("192.168.0.5", pickLocalHost(listOf("169.254.10.20", "192.168.0.5")))
    }

    @Test
    fun `에뮬레이터 NAT 주소는 다른 사설 주소보다 뒤로 밀린다`() {
        // 10.0.2.15 는 에뮬레이터 안에서만 뜻이 있다.
        val picked = pickLocalHost(listOf("10.0.2.15", "10.5.0.7"))

        assertEquals("10.5.0.7", picked)
    }

    @Test
    fun `사설 주소가 하나도 없으면 공인 주소라도 준다`() {
        // 없는 것보다는 낫다 — 테더링이나 공인 IP 를 받은 상황일 수 있다.
        val picked = pickLocalHost(listOf("203.0.113.7"))

        assertEquals("203.0.113.7", picked)
    }

    @Test
    fun `같은 순위면 열거된 순서를 유지한다`() {
        val picked = pickLocalHost(listOf("192.168.0.5", "192.168.1.9"))

        assertEquals("192.168.0.5", picked)
    }
}
