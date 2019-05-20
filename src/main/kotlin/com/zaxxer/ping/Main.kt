package com.zaxxer.ping

import jnr.ffi.Platform
import java.io.BufferedReader
import java.io.InputStreamReader
import java.lang.management.ManagementFactory
import java.net.Inet6Address
import java.net.InetAddress
import java.util.concurrent.Semaphore
import com.sun.management.UnixOperatingSystemMXBean




fun main() {
    val os = ManagementFactory.getOperatingSystemMXBean()
    if (os is UnixOperatingSystemMXBean) {
        println(os.getMaxFileDescriptorCount());
    }
//    pingTestIpv6()
}

fun pingTestIpv6() {
    val runtime: jnr.ffi.Runtime = jnr.ffi.Runtime.getSystemRuntime()!!
    val platform: Platform = Platform.getNativePlatform()
    val isBSD = platform.isBSD

    val semaphore = Semaphore(2)
    val timeoutTargets = HashSet<PingTarget>()

    class PingHandler : PingResponseHandler {
        override fun onResponse(pingTarget: PingTarget, responseTimeSec: Double, byteCount: Int, seq: Int) {
            System.out.printf("  ${Thread.currentThread()} $byteCount bytes from ${pingTarget.toString()
                    .replace("%", "%%")}: icmp_seq=$seq time=%1.6f\n", responseTimeSec)
            println("  ${Thread.currentThread()} Calling semaphore.release()\n")
            semaphore.release()
        }

        override fun onTimeout(pingTarget: PingTarget) {
            println("  ${Thread.currentThread()} Timeout")
            timeoutTargets.add(pingTarget)
            semaphore.release()
        }
    }

    val pinger = IcmpPinger(PingHandler())

    val selectorThread = Thread({ pinger.runSelector() })
    selectorThread.isDaemon = false
    selectorThread.start()

    val pingTargets = ArrayList<PingTarget>()
    if (isBSD) {
        pingTargets.add(PingTarget(InetAddress.getByName("2001:4860:4860::8888")))
    } else {
        val address = getIpv6Address()
        if (address == null) {
            println("IPv6 is not configured properly.")
        }
        pingTargets.add(PingTarget(address!!))
    }

    for (i in 0..(10 * pingTargets.size)) {
        if (!semaphore.tryAcquire()) {
            println("$i: Blocking on semaphore.acquire()")
            semaphore.acquire()
        }
        println("$i: Calling pinger.ping(${pingTargets[i % pingTargets.size].inetAddress})")
        pinger.ping(pingTargets[i % pingTargets.size])
    }

    while (pinger.isPendingWork()) Thread.sleep(500)

    pinger.stopSelector()

    println("$timeoutTargets timed out.")
}

private fun getIpv6Address(): InetAddress? {
    val proc = Runtime.getRuntime().exec("ip -6 addr show")
    val stdInput = BufferedReader(InputStreamReader(proc.inputStream))
    val addressOptional = stdInput.lines().filter { it.contains("inet6") && !it.contains("::1/128") }.findFirst()
    val address = if (addressOptional.isPresent) {
        addressOptional.get().trim().split(" ")[1].split("/")[0]
    } else null
    return InetAddress.getByName(address) as? Inet6Address ?: return null
}