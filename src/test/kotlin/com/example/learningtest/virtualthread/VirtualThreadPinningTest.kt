package com.example.learningtest.virtualthread

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.longs.shouldBeLessThan
import io.kotest.matchers.shouldBe
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.locks.ReentrantLock
import kotlin.system.measureTimeMillis

/**
 * Pinning 현상을 확인한다.
 *
 * 핵심 포인트:
 * - 가상 스레드가 synchronized 블록 안에서 블로킹(sleep 등)하면
 *   캐리어 스레드에 "고정(pinned)"되어 반납되지 않는다.
 * - 이렇게 되면 가상 스레드의 장점(M:N 스케줄링)이 사라진다.
 * - 해결책: synchronized 대신 ReentrantLock을 사용하면 pinning이 발생하지 않는다.
 *
 * Java 24+에서는 synchronized pinning이 해결되었지만,
 * 개념적으로 이해하기 위해 테스트를 작성한다.
 */
class VirtualThreadPinningTest : FunSpec({

    val lock = Any()
    val reentrantLock = ReentrantLock()

    test("synchronized 블록 안에서 sleep하면 pinning이 발생할 수 있다 (Java 24+ 에서는 해결됨)") {
        // Java 24 이전: synchronized 안에서 sleep 시 캐리어 스레드가 고정됨
        // Java 24+: synchronized에서도 pinning이 해결됨
        // 하지만 개념을 이해하기 위해 두 방식을 비교한다
        val taskCount = 200
        val completed = AtomicInteger(0)

        val elapsed = measureTimeMillis {
            val threads = (1..taskCount).map {
                Thread.startVirtualThread {
                    synchronized(lock) {
                        Thread.sleep(10) // 블로킹
                    }
                    completed.incrementAndGet()
                }
            }
            threads.forEach { it.join() }
        }

        completed.get() shouldBe taskCount
        println("[synchronized] ${taskCount}개 작업: ${elapsed}ms")
    }

    test("ReentrantLock을 사용하면 pinning 없이 캐리어 스레드를 효율적으로 사용한다") {
        val taskCount = 200
        val completed = AtomicInteger(0)

        val elapsed = measureTimeMillis {
            val threads = (1..taskCount).map {
                Thread.startVirtualThread {
                    reentrantLock.lock()
                    try {
                        Thread.sleep(10) // 블로킹이지만 pinning 없음
                    } finally {
                        reentrantLock.unlock()
                    }
                    completed.incrementAndGet()
                }
            }
            threads.forEach { it.join() }
        }

        completed.get() shouldBe taskCount
        println("[ReentrantLock] ${taskCount}개 작업: ${elapsed}ms")
    }

    test("synchronized vs ReentrantLock: 동시 블로킹 작업에서의 성능 차이") {
        // 락을 여러 개 만들어서 동시성을 높인 상황에서 비교
        val taskCount = 500
        val locksCount = 10
        val syncLocks = Array(locksCount) { Any() }
        val reentrantLocks = Array(locksCount) { ReentrantLock() }

        val syncCounter = AtomicInteger(0)
        val syncElapsed = measureTimeMillis {
            val threads = (1..taskCount).map { i ->
                Thread.startVirtualThread {
                    synchronized(syncLocks[i % locksCount]) {
                        Thread.sleep(10)
                    }
                    syncCounter.incrementAndGet()
                }
            }
            threads.forEach { it.join() }
        }

        val reentrantCounter = AtomicInteger(0)
        val reentrantElapsed = measureTimeMillis {
            val threads = (1..taskCount).map { i ->
                Thread.startVirtualThread {
                    reentrantLocks[i % locksCount].lock()
                    try {
                        Thread.sleep(10)
                    } finally {
                        reentrantLocks[i % locksCount].unlock()
                    }
                    reentrantCounter.incrementAndGet()
                }
            }
            threads.forEach { it.join() }
        }

        syncCounter.get() shouldBe taskCount
        reentrantCounter.get() shouldBe taskCount

        println("${taskCount}개 작업, ${locksCount}개 락:")
        println("  synchronized:  ${syncElapsed}ms")
        println("  ReentrantLock: ${reentrantElapsed}ms")
        // Java 24+에서는 차이가 거의 없을 수 있다 (synchronized pinning 해결됨)
        // Java 21-23에서는 ReentrantLock이 훨씬 빠를 것이다
    }

    test("pinning 없는 상황에서는 가상 스레드의 동시성이 최대로 발휘된다") {
        // 락 없이 순수 블로킹만 하면 가상 스레드가 캐리어를 즉시 반납한다
        val taskCount = 1_000
        val completed = AtomicInteger(0)

        val elapsed = measureTimeMillis {
            val threads = (1..taskCount).map {
                Thread.startVirtualThread {
                    Thread.sleep(100) // 캐리어 스레드 반납
                    completed.incrementAndGet()
                }
            }
            threads.forEach { it.join() }
        }

        completed.get() shouldBe taskCount
        // 1000개 × 100ms를 전부 동시 처리 → 약 100ms + 오버헤드
        elapsed.shouldBeLessThan(3_000)
        println("[no lock] ${taskCount}개 작업 (각 100ms sleep): ${elapsed}ms")
    }
})
