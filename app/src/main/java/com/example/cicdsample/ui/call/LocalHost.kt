package com.example.cicdsample.ui.call

import java.net.Inet4Address
import java.net.NetworkInterface
import javax.inject.Inject

/**
 * 내 단말의 IP 를 알려준다.
 *
 * 도메인이 아니라 화면 쪽에 두는 이유는 계약이 그렇게 생겼기 때문이다 —
 * `BuildLocalSdpUseCase` 는 localHost 를 **인자로 받는다.** 도메인이 네트워크 인터페이스를
 * 뒤지기 시작하면 순수 함수로 남지 못한다.
 *
 * 인터페이스로 떼어 둔 것은 테스트에서 고정된 주소를 꽂기 위해서다 — 실기기 주소는 매번 다르다.
 */
fun interface LocalHostProvider {
    /** @return 상대가 닿을 수 있는 내 IPv4 주소. 찾지 못하면 null. */
    fun localHost(): String?
}

/** 실제 네트워크 인터페이스를 열거한다. 고르는 규칙은 [pickLocalHost] 가 따로 갖는다. */
class NetworkLocalHostProvider @Inject constructor() : LocalHostProvider {

    override fun localHost(): String? = pickLocalHost(candidates())

    /**
     * 올라와 있는 인터페이스의 IPv4 주소만 모은다.
     *
     * IPv6 를 쓰지 않는 이유는 사람이 옮겨 적기 때문이다 — SDP 는 IPv6 도 되지만
     * `fe80::1c4b:2aff:fe12:3456%wlan0` 을 전화로 읽어 주기는 어렵다.
     */
    private fun candidates(): List<String> = runCatching {
        NetworkInterface.getNetworkInterfaces().asSequence()
            .filter { it.isUp && !it.isLoopback }
            .flatMap { it.inetAddresses.asSequence() }
            .filterIsInstance<Inet4Address>()
            .mapNotNull { it.hostAddress }
            .toList()
    }.getOrDefault(emptyList()) // 권한·기기 상태로 열거가 실패하면 주소를 못 찾은 것과 같다.
}

/**
 * 후보 중에서 **상대가 실제로 닿을 수 있는** 주소를 고른다.
 *
 * 이 앱의 사용 상황은 "같은 Wi-Fi 에 있는 두 기기"다. VPN·테더링·에뮬레이터가 섞이면
 * 후보가 여러 개 나오는데, 사용자는 그중 무엇을 상대에게 건네야 하는지 알 수 없다.
 * 그래서 규칙을 코드로 고정하고, 순수 함수로 떼어 기기 없이 테스트한다.
 *
 * 우선순위는 "닿을 가능성이 높은 순"이다.
 */
fun pickLocalHost(candidates: List<String>): String? = candidates
    // 링크 로컬(169.254.x.x)은 DHCP 를 못 받았을 때 붙는 주소다. 상대는 닿지 못한다.
    .filterNot { it.startsWith(LINK_LOCAL_PREFIX) }
    .minByOrNull { reachabilityRank(it) }

/** 작을수록 먼저 고른다. 같은 순위면 열거된 순서를 유지한다. */
private fun reachabilityRank(address: String): Int = when {
    // 가정용 Wi-Fi 에서 가장 흔하다.
    address.startsWith("192.168.") -> 0

    address.isPrivateClassB() -> 1

    // 에뮬레이터의 NAT 주소. 에뮬레이터 안에서만 뜻이 있고 밖에서는 닿지 않으므로
    // 다른 사설 주소가 하나라도 있으면 그쪽을 고른다.
    address.startsWith(EMULATOR_NAT_PREFIX) -> 3

    address.startsWith("10.") -> 2

    // 공인 주소나 처음 보는 대역. 그래도 없는 것보다는 낫다.
    else -> 4
}

/** 172.16.0.0 ~ 172.31.255.255 — 사설 B 클래스. 172.32.x 부터는 공인이다. */
private fun String.isPrivateClassB(): Boolean {
    if (!startsWith("172.")) return false
    val second = split('.').getOrNull(1)?.toIntOrNull() ?: return false
    return second in 16..31
}

private const val LINK_LOCAL_PREFIX = "169.254."
private const val EMULATOR_NAT_PREFIX = "10.0.2."
