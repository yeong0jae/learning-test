package com.example.learningtest.virtualthread

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicLong
import kotlin.system.measureTimeMillis

/**
 * CPU-bound 작업에서 가상 스레드는 이점이 없음을 확인한다.
 *
 * 핵심 포인트:
 * - 가상 스레드의 장점은 I/O 블로킹 시 캐리어 스레드를 반납하는 것이다.
 * - CPU-bound 작업은 블로킹이 없으므로 캐리어 스레드를 계속 점유한다.
 * - 따라서 CPU-bound 작업에서는 가상 스레드 = 플랫폼 스레드와 성능 차이가 없다.
 * - 오히려 가상 스레드 스케줄링 오버헤드가 추가될 수 있다.
 *
 * 결론: 가상 스레드는 I/O-bound 워크로드에 최적화되어 있다.
 */
class VirtualThreadCPUBoundTest : FunSpec({

    // CPU 집중 작업 시뮬레이션: 소수 판별
    fun isPrime(n: Long): Boolean {
        if (n < 2) return false
        var i = 2L
        while (i * i <= n) {
            if (n % i == 0L) return false
            i++
        }
        return true
    }

    // 범위 내 소수 개수 세기 (CPU 부하)
    fun countPrimes(from: Long, to: Long): Long {
        var count = 0L
        for (n in from..to) {
            if (isPrime(n)) count++
        }
        return count
    }

    test("CPU-bound 작업: 고정 스레드풀과 가상 스레드의 성능이 비슷하다") {
        val taskCount = Runtime.getRuntime().availableProcessors()
        val rangeSize = 50_000L
        val fixedResult = AtomicLong(0)
        val virtualResult = AtomicLong(0)

        // 고정 스레드풀 (코어 수만큼)
        val fixedElapsed = measureTimeMillis {
            Executors.newFixedThreadPool(taskCount).use { executor ->
                val futures = (0 until taskCount).map { i ->
                    val from = i * rangeSize + 1
                    val to = (i + 1) * rangeSize
                    executor.submit<Long> { countPrimes(from, to) }
                }
                futures.forEach { fixedResult.addAndGet(it.get()) }
            }
        }

        // 가상 스레드
        val virtualElapsed = measureTimeMillis {
            Executors.newVirtualThreadPerTaskExecutor().use { executor ->
                val futures = (0 until taskCount).map { i ->
                    val from = i * rangeSize + 1
                    val to = (i + 1) * rangeSize
                    executor.submit<Long> { countPrimes(from, to) }
                }
                futures.forEach { virtualResult.addAndGet(it.get()) }
            }
        }

        // 결과는 같아야 한다
        fixedResult.get() shouldBe virtualResult.get()

        println("CPU-bound 작업 (소수 계산, ${taskCount}개 분할)")
        println("  고정 스레드풀(${taskCount}): ${fixedElapsed}ms")
        println("  가상 스레드:              ${virtualElapsed}ms")
        println("  결과: ${fixedResult.get()}개 소수 발견")
        // 두 방식의 시간이 비슷하거나, 가상 스레드가 약간 느릴 수 있다
    }

    test("I/O-bound vs CPU-bound: 가상 스레드가 빛나는 상황과 아닌 상황") {
        val taskCount = 100

        // I/O-bound: 가상 스레드가 압도적으로 빠르다
        val fixedIO = measureTimeMillis {
            Executors.newFixedThreadPool(10).use { executor ->
                val futures = (1..taskCount).map {
                    executor.submit { Thread.sleep(50) } // I/O 시뮬레이션
                }
                futures.forEach { it.get() }
            }
        }

        val virtualIO = measureTimeMillis {
            Executors.newVirtualThreadPerTaskExecutor().use { executor ->
                val futures = (1..taskCount).map {
                    executor.submit { Thread.sleep(50) } // I/O 시뮬레이션
                }
                futures.forEach { it.get() }
            }
        }

        // CPU-bound: 차이가 거의 없거나 고정 풀이 나을 수도 있다
        val fixedCPU = measureTimeMillis {
            Executors.newFixedThreadPool(10).use { executor ->
                val futures = (1..taskCount).map {
                    executor.submit { countPrimes(1, 10_000) }
                }
                futures.forEach { it.get() }
            }
        }

        val virtualCPU = measureTimeMillis {
            Executors.newVirtualThreadPerTaskExecutor().use { executor ->
                val futures = (1..taskCount).map {
                    executor.submit { countPrimes(1, 10_000) }
                }
                futures.forEach { it.get() }
            }
        }

        println("=== I/O-bound (100개 × 50ms sleep) ===")
        println("  고정 스레드풀(10): ${fixedIO}ms")
        println("  가상 스레드:       ${virtualIO}ms ← 여기서 가상 스레드가 빛남")
        println()
        println("=== CPU-bound (100개 × 소수 계산) ===")
        println("  고정 스레드풀(10): ${fixedCPU}ms")
        println("  가상 스레드:       ${virtualCPU}ms ← 별 차이 없음")
    }
})
