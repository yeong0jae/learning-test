package com.example.learningtest.virtualthread

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.longs.shouldBeLessThan
import io.kotest.matchers.shouldBe
import io.kotest.matchers.comparables.shouldBeGreaterThan
import java.util.concurrent.atomic.AtomicInteger
import kotlin.system.measureTimeMillis

/**
 * 플랫폼 스레드 vs 가상 스레드의 생성 한계를 비교한다.
 *
 * 핵심 포인트:
 * - 플랫폼 스레드는 각각 OS 스레드 + 스택 메모리(기본 ~1MB)를 소비한다.
 *   수천 개만 만들어도 OutOfMemoryError가 발생할 수 있다.
 * - 가상 스레드는 JVM 힙의 작은 객체일 뿐이다.
 *   수십만 개를 만들어도 문제없다.
 */
class VirtualThreadScalabilityTest : FunSpec({

    test("플랫폼 스레드는 수천 개 생성 시 시간이 오래 걸리고, 가상 스레드는 10만 개도 빠르다") {
        val platformCount = 2_000 // 플랫폼 스레드는 2천 개만
        val virtualCount = 100_000 // 가상 스레드는 10만 개

        val platformCounter = AtomicInteger(0)
        val virtualCounter = AtomicInteger(0)

        val platformElapsed = measureTimeMillis {
            val threads = (1..platformCount).map {
                Thread.ofPlatform().start { platformCounter.incrementAndGet() }
            }
            threads.forEach { it.join() }
        }

        val virtualElapsed = measureTimeMillis {
            val threads = (1..virtualCount).map {
                Thread.startVirtualThread { virtualCounter.incrementAndGet() }
            }
            threads.forEach { it.join() }
        }

        platformCounter.get() shouldBe platformCount
        virtualCounter.get() shouldBe virtualCount

        // 가상 스레드 10만 개가 플랫폼 스레드 2천 개보다 빠르거나 비슷해야 한다
        println("플랫폼 스레드 ${platformCount}개: ${platformElapsed}ms")
        println("가상 스레드 ${virtualCount}개: ${virtualElapsed}ms")
    }

    test("가상 스레드 10만 개가 각각 sleep해도 수 초 내 완료된다 - 캐리어 스레드를 반납하기 때문") {
        val threadCount = 100_000
        val count = AtomicInteger(0)

        val elapsed = measureTimeMillis {
            val threads = (1..threadCount).map {
                Thread.startVirtualThread {
                    Thread.sleep(100) // blocking이지만 캐리어 스레드 반납
                    count.incrementAndGet()
                }
            }
            threads.forEach { it.join() }
        }

        count.get() shouldBe threadCount
        // 플랫폼 스레드였다면 10만 * 100ms는 OS 스레드 부족으로 불가능하지만,
        // 가상 스레드는 소수의 캐리어 스레드로 전부 처리한다
        elapsed.shouldBeLessThan(10_000)
        println("가상 스레드 ${threadCount}개 (각 100ms sleep): ${elapsed}ms")
    }

    test("플랫폼 스레드 2천 개가 각각 100ms sleep하면 가상 스레드보다 훨씬 느리다") {
        val platformCount = 2_000
        val platformCounter = AtomicInteger(0)
        val virtualCounter = AtomicInteger(0)

        val platformElapsed = measureTimeMillis {
            val threads = (1..platformCount).map {
                Thread.ofPlatform().start {
                    Thread.sleep(100)
                    platformCounter.incrementAndGet()
                }
            }
            threads.forEach { it.join() }
        }

        // 같은 수의 가상 스레드와 비교
        val virtualElapsed = measureTimeMillis {
            val threads = (1..platformCount).map {
                Thread.startVirtualThread {
                    Thread.sleep(100)
                    virtualCounter.incrementAndGet()
                }
            }
            threads.forEach { it.join() }
        }

        platformCounter.get() shouldBe platformCount
        virtualCounter.get() shouldBe platformCount

        // 가상 스레드가 같은 작업을 더 빠르게 완료해야 한다
        println("플랫폼 스레드 ${platformCount}개 (100ms sleep): ${platformElapsed}ms")
        println("가상 스레드 ${platformCount}개 (100ms sleep): ${virtualElapsed}ms")
    }
})
