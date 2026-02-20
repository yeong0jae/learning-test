package com.example.learningtest.virtualthread

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.longs.shouldBeLessThan
import io.kotest.matchers.shouldBe
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import kotlin.system.measureTimeMillis

/**
 * I/O 블로킹 시 고정 스레드풀 vs 가상 스레드의 처리량 차이를 비교한다.
 *
 * 핵심 포인트:
 * - 고정 크기 스레드풀(예: 10개)은 10개씩 순차적으로 처리한다.
 *   100개 작업 × 100ms = 약 1000ms (10배치)
 * - 가상 스레드는 전부 동시 처리한다.
 *   100개 작업 × 100ms ≈ 약 100ms (전부 동시)
 * - 이것이 가능한 이유: 가상 스레드가 I/O 블로킹 시 캐리어 스레드를 반납하기 때문
 */
class VirtualThreadIOThroughputTest : FunSpec({

    test("고정 스레드풀 10개로 100개 I/O 작업 처리 - 10개씩 순차 배치 처리") {
        val taskCount = 100
        val completed = AtomicInteger(0)

        val elapsed = measureTimeMillis {
            Executors.newFixedThreadPool(10).use { executor ->
                val futures = (1..taskCount).map {
                    executor.submit {
                        Thread.sleep(100) // I/O 시뮬레이션
                        completed.incrementAndGet()
                    }
                }
                futures.forEach { it.get() }
            }
        }

        completed.get() shouldBe taskCount
        // 10개 풀 × 100ms × 10배치 ≈ 1000ms
        println("고정 스레드풀(10) - ${taskCount}개 작업: ${elapsed}ms")
    }

    test("가상 스레드로 100개 I/O 작업 처리 - 전부 동시 처리") {
        val taskCount = 100
        val completed = AtomicInteger(0)

        val elapsed = measureTimeMillis {
            Executors.newVirtualThreadPerTaskExecutor().use { executor ->
                val futures = (1..taskCount).map {
                    executor.submit {
                        Thread.sleep(100) // I/O 시뮬레이션
                        completed.incrementAndGet()
                    }
                }
                futures.forEach { it.get() }
            }
        }

        completed.get() shouldBe taskCount
        // 전부 동시 처리이므로 ≈ 100ms + 약간의 오버헤드
        println("가상 스레드 - ${taskCount}개 작업: ${elapsed}ms")
        elapsed.shouldBeLessThan(500) // 100ms + 오버헤드
    }

    test("1000개 I/O 작업: 고정풀 vs 가상 스레드 처리 시간 비교") {
        val taskCount = 1_000

        // 고정 스레드풀 (20개)
        val fixedCounter = AtomicInteger(0)
        val fixedElapsed = measureTimeMillis {
            Executors.newFixedThreadPool(20).use { executor ->
                val futures = (1..taskCount).map {
                    executor.submit {
                        Thread.sleep(50) // I/O 시뮬레이션
                        fixedCounter.incrementAndGet()
                    }
                }
                futures.forEach { it.get() }
            }
        }

        // 가상 스레드
        val virtualCounter = AtomicInteger(0)
        val virtualElapsed = measureTimeMillis {
            Executors.newVirtualThreadPerTaskExecutor().use { executor ->
                val futures = (1..taskCount).map {
                    executor.submit {
                        Thread.sleep(50) // I/O 시뮬레이션
                        virtualCounter.incrementAndGet()
                    }
                }
                futures.forEach { it.get() }
            }
        }

        fixedCounter.get() shouldBe taskCount
        virtualCounter.get() shouldBe taskCount

        // 고정풀: 1000/20 = 50배치 × 50ms = 약 2500ms
        // 가상: 전부 동시 ≈ 약 50ms + 오버헤드
        println("1000개 I/O 작업 (각 50ms)")
        println("  고정 스레드풀(20): ${fixedElapsed}ms")
        println("  가상 스레드:       ${virtualElapsed}ms")
        println("  속도 향상:         ${fixedElapsed / maxOf(virtualElapsed, 1)}배")
    }

    test("가상 스레드의 I/O 처리량은 동시 작업 수에 거의 비례하지 않는다") {
        // 100개든 1000개든 I/O 대기 시간은 거의 동일
        val counter100 = AtomicInteger(0)
        val counter1000 = AtomicInteger(0)

        val elapsed100 = measureTimeMillis {
            Executors.newVirtualThreadPerTaskExecutor().use { executor ->
                val futures = (1..100).map {
                    executor.submit {
                        Thread.sleep(100)
                        counter100.incrementAndGet()
                    }
                }
                futures.forEach { it.get() }
            }
        }

        val elapsed1000 = measureTimeMillis {
            Executors.newVirtualThreadPerTaskExecutor().use { executor ->
                val futures = (1..1_000).map {
                    executor.submit {
                        Thread.sleep(100)
                        counter1000.incrementAndGet()
                    }
                }
                futures.forEach { it.get() }
            }
        }

        counter100.get() shouldBe 100
        counter1000.get() shouldBe 1_000

        // 작업 수가 10배 늘어도 전체 시간은 비슷해야 한다
        println("가상 스레드 I/O 100개: ${elapsed100}ms")
        println("가상 스레드 I/O 1000개: ${elapsed1000}ms")
        elapsed1000.shouldBeLessThan(3_000) // 1000개도 3초 미만
    }
})
