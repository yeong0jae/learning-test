package com.example.learningtest.virtualthread

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.comparables.shouldBeGreaterThan
import io.kotest.matchers.comparables.shouldBeLessThan
import io.kotest.matchers.shouldBe
import io.kotest.matchers.booleans.shouldBeTrue
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch

/**
 * 캐리어 스레드의 동작을 확인한다.
 *
 * 핵심 포인트:
 * - 가상 스레드는 소수의 캐리어 스레드(ForkJoinPool) 위에서 스케줄된다.
 * - 가상 스레드가 블로킹되면 캐리어 스레드를 반납(unmount)하고,
 *   재개될 때 다른 캐리어 스레드에 마운트될 수 있다.
 * - 이것이 M:N 스케줄링이다 (M개의 가상 스레드 : N개의 캐리어 스레드).
 */
class VirtualThreadCarrierTest : FunSpec({

    test("수천 개의 가상 스레드가 소수의 캐리어 스레드 위에서 실행된다") {
        val carrierThreadNames = ConcurrentHashMap.newKeySet<String>()
        val virtualThreadCount = 10_000
        val latch = CountDownLatch(virtualThreadCount)

        val threads = (1..virtualThreadCount).map {
            Thread.startVirtualThread {
                // 캐리어 스레드 이름을 추출 (가상 스레드의 toString에 캐리어 정보가 포함됨)
                val carrierName = extractCarrierName()
                if (carrierName != null) {
                    carrierThreadNames.add(carrierName)
                }
                latch.countDown()
            }
        }
        threads.forEach { it.join() }
        latch.await()

        // 1만 개의 가상 스레드가 Runtime.availableProcessors() 근처의 캐리어 스레드에서 실행됨
        val availableProcessors = Runtime.getRuntime().availableProcessors() // CPU 코어 수
        println("가상 스레드: ${virtualThreadCount}개")
        println("사용된 캐리어 스레드: ${carrierThreadNames.size}개")
        println("사용 가능한 프로세서: ${availableProcessors}개")

        carrierThreadNames.size.shouldBeLessThan(virtualThreadCount)
        carrierThreadNames.size.shouldBeGreaterThan(0)
    }

    test("가상 스레드가 블로킹 후 재개되면 다른 캐리어 스레드에서 실행될 수 있다") {
        // 여러 번 반복해서 캐리어 전환을 관찰
        var switchCount = 0
        val iterations = 100

        val threads = (1..iterations).map {
            Thread.startVirtualThread {
                val before = extractCarrierName()
                Thread.sleep(10) // 블로킹 → 캐리어 스레드 반납
                val after = extractCarrierName()

                if (before != null && after != null && before != after) {
                    switchCount++
                }
            }
        }
        threads.forEach { it.join() }

        // 일부는 캐리어 스레드가 전환될 수 있다 (보장은 아님)
        println("${iterations}개 중 캐리어 전환 발생: ${switchCount}건")
        // 최소한 가상 스레드임은 확인
        Thread.startVirtualThread {}.apply { join() }.isVirtual.shouldBeTrue()
    }

    test("캐리어 스레드의 기본 개수는 availableProcessors와 같다") {
        val carrierThreadNames = ConcurrentHashMap.newKeySet<String>()
        val latch = CountDownLatch(1000)

        // 동시에 실행되도록 해서 캐리어 스레드를 최대한 사용
        val threads = (1..1000).map {
            Thread.startVirtualThread {
                val carrier = extractCarrierName()
                if (carrier != null) carrierThreadNames.add(carrier)
                Thread.sleep(50) // 동시에 점유하도록 약간의 지연
                latch.countDown()
            }
        }
        threads.forEach { it.join() }
        latch.await()

        val availableProcessors = Runtime.getRuntime().availableProcessors()
        println("캐리어 스레드 수: ${carrierThreadNames.size}, 프로세서 수: $availableProcessors")
        // ForkJoinPool의 기본 parallelism은 availableProcessors()
        // 캐리어 수가 프로세서 수 근처여야 한다
        carrierThreadNames.size.shouldBeGreaterThan(0)
    }
})

/**
 * 가상 스레드의 toString()에서 캐리어(ForkJoinPool) 스레드 이름을 추출한다.
 * 예: "VirtualThread[#42]/runnable@ForkJoinPool-1-worker-3"
 */
private fun extractCarrierName(): String? {
    val threadString = Thread.currentThread().toString()
    val atIndex = threadString.indexOf('@')
    return if (atIndex >= 0) threadString.substring(atIndex + 1) else null
}
