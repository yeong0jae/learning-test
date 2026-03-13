package com.example.learningtest.virtualthread

import io.kotest.core.spec.style.FunSpec
import java.util.concurrent.CountDownLatch
import java.util.concurrent.locks.ReentrantLock
import kotlin.system.measureTimeMillis

/**
 * Pinning으로 인한 Starvation(기아) 현상을 확인한다.
 *
 * 핵심 포인트:
 * - 캐리어 스레드(물리적 스레드)의 개수를 딱 2개로 제한한다.
 * - 가상 스레드 2개가 락을 잡고 2초 동안 블로킹된다.
 * - 이때 "락과 전혀 무관한 3번째 가상 스레드"가 실행될 때,
 * 캐리어 스레드를 할당받지 못해 얼마나 굶주리는지(Starvation) 확인한다.
 */
class VirtualThreadStarvationTest : FunSpec({

    // 가상 스레드 스케줄러가 초기화되기 전에 캐리어 스레드 수를 2개로 제한합니다.
    // 만약 테스트 결과가 예상과 다르게 나온다면, IDE의 Run Configuration에서
    // VM options에 직접 `-Djdk.virtualThreadScheduler.parallelism=2` 를 추가하고 실행해 주세요.
    System.setProperty("jdk.virtualThreadScheduler.parallelism", "2")

    val syncLock1 = Any()
    val syncLock2 = Any()
    val reentrantLock1 = ReentrantLock()
    val reentrantLock2 = ReentrantLock()

    test("1. [synchronized] Pinning 발생 시, 무관한 스레드까지 캐리어 스레드를 얻지 못해 멈춘다") {
        val latch = CountDownLatch(2)

        // 캐리어 스레드 2개를 모두 점유하고 2초 동안 놔주지 않는 가상 스레드 2개
        val pinnedThreads = (1..2).map { i ->
            Thread.startVirtualThread {
                val lock = if (i == 1) syncLock1 else syncLock2
                synchronized(lock) {
                    latch.countDown()
                    Thread.sleep(2000) // 2초간 캐리어 스레드에 강력 접착 (Pinning!)
                }
            }
        }

        // 앞선 2개의 스레드가 락을 잡고 sleep에 들어갈 때까지 아주 잠깐 대기
        latch.await()
        Thread.sleep(50)

        // 락과 완전히 무관한 3번째 가상 스레드 투입! (단순 100ms 대기 작업)
        val elapsed = measureTimeMillis {
            val innocentThread = Thread.startVirtualThread {
                Thread.sleep(100)
            }
            innocentThread.join()
        }

        println("[synchronized] 무관한 3번째 작업이 걸린 시간: ${elapsed}ms")
        // Java 21~23: 앞선 2초 작업이 끝날 때까지 캐리어 스레드가 없어서 약 1950ms 이상 걸림
        // Java 24+ : Pinning이 해결되어 즉시 실행되므로 약 100ms 언저리 걸림
    }

    test("2. [ReentrantLock] 언마운트 발생 시, 무관한 스레드는 즉시 빈 캐리어 스레드를 사용한다") {
        val latch = CountDownLatch(2)

        // 락을 잡고 쉬는 동안 캐리어 스레드를 깔끔하게 반납하는 가상 스레드 2개
        val wellBehavedThreads = (1..2).map { i ->
            Thread.startVirtualThread {
                val lock = if (i == 1) reentrantLock1 else reentrantLock2
                lock.lock()
                try {
                    latch.countDown()
                    Thread.sleep(2000) // 힙 메모리로 언마운트! 캐리어 스레드 2개 해방!
                } finally {
                    lock.unlock()
                }
            }
        }

        latch.await()
        Thread.sleep(50)

        // 락과 완전히 무관한 3번째 가상 스레드 투입!
        val elapsed = measureTimeMillis {
            val innocentThread = Thread.startVirtualThread {
                Thread.sleep(100)
            }
            innocentThread.join()
        }

        println("[ReentrantLock] 무관한 3번째 작업이 걸린 시간: ${elapsed}ms")
        // 캐리어 스레드가 비어있으므로 앞선 2초 작업과 상관없이 즉시 실행됨 (약 100ms)
    }
})
