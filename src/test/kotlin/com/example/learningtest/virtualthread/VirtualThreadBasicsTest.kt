package com.example.learningtest.virtualthread

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.shouldBe
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors

/**
 * 가상 스레드 생성/실행의 3가지 핵심 방법을 학습한다.
 *
 * 1) Thread.startVirtualThread() — 즉시 시작하는 가장 간단한 방법
 * 2) Thread.ofVirtual() — 빌더 패턴으로 이름, 핸들러 등을 설정
 * 3) Executors.newVirtualThreadPerTaskExecutor() — 기존 스레드풀 코드를 가상 스레드로 전환
 *
 * 셋 다 결국 가상 스레드를 만드는 것이고,
 * ExecutorService는 기존 코드와의 호환을 위한 편의 API이다.
 */
class VirtualThreadBasicsTest : FunSpec({

    // ===== 1. Thread.startVirtualThread =====

    test("Thread.startVirtualThread는 가상 스레드를 즉시 생성하고 시작한다") {
        var result = ""

        val thread = Thread.startVirtualThread { result = "hello from virtual" }
        thread.join()

        result shouldBe "hello from virtual"
        thread.isVirtual.shouldBeTrue()
    }

    // ===== 2. Thread.ofVirtual() 빌더 =====

    test("Thread.ofVirtual()로 가상 스레드를 생성하고, ofPlatform()으로 플랫폼 스레드를 생성한다") {
        val virtualThread = Thread.ofVirtual().unstarted { }
        val platformThread = Thread.ofPlatform().unstarted { }

        virtualThread.isVirtual.shouldBeTrue()
        platformThread.isVirtual.shouldBeFalse()
    }

    test("ofVirtual 빌더로 이름, UncaughtExceptionHandler 등을 설정할 수 있다") {
        var threadName = ""
        var caughtException: Throwable? = null

        val thread = Thread.ofVirtual()
            .name("my-worker")
            .uncaughtExceptionHandler { _, e -> caughtException = e }
            .start {
                threadName = Thread.currentThread().name
                throw IllegalStateException("boom")
            }
        thread.join()

        threadName shouldBe "my-worker"
        caughtException!!.message shouldBe "boom"
    }

    test("ofVirtual 빌더의 name(prefix, start)로 스레드 팩토리를 만들면 자동 번호가 붙는다") {
        val names = ConcurrentHashMap.newKeySet<String>()
        val latch = CountDownLatch(3)
        val factory = Thread.ofVirtual().name("worker-", 0).factory()

        val threads = (0 until 3).map {
            factory.newThread {
                names.add(Thread.currentThread().name)
                latch.countDown()
            }.also { it.start() }
        }
        threads.forEach { it.join() }
        latch.await()

        names.sorted().toList() shouldBe listOf("worker-0", "worker-1", "worker-2")
    }

    // ===== 3. Executors.newVirtualThreadPerTaskExecutor =====

    test("newVirtualThreadPerTaskExecutor는 작업마다 새 가상 스레드를 생성한다") {
        val threadIds = ConcurrentHashMap.newKeySet<Long>()

        Executors.newVirtualThreadPerTaskExecutor().use { executor ->
            val futures = (1..100).map {
                executor.submit {
                    threadIds.add(Thread.currentThread().threadId())
                    Thread.currentThread().isVirtual.shouldBeTrue()
                }
            }
            futures.forEach { it.get() }
        }

        // 각 작업이 서로 다른 가상 스레드에서 실행됨 — 풀을 재사용하지 않는다
        threadIds.size shouldBe 100
    }

    test("기존 fixedThreadPool 코드를 newVirtualThreadPerTaskExecutor로 교체하면 동일하게 동작한다") {
        // 기존 방식: Executors.newFixedThreadPool(10)
        // 가상 스레드 방식: Executors.newVirtualThreadPerTaskExecutor()
        // API가 같으므로 한 줄만 바꾸면 된다
        val results = ConcurrentHashMap.newKeySet<String>()

        Executors.newVirtualThreadPerTaskExecutor().use { executor ->
            val futures = (1..5).map { i ->
                executor.submit<String> {
                    Thread.currentThread().isVirtual.shouldBeTrue()
                    "task-$i"
                }
            }
            futures.forEach { results.add(it.get()) }
        }

        results shouldBe setOf("task-1", "task-2", "task-3", "task-4", "task-5")
    }

    // ===== 가상 스레드의 기본 속성 =====

    test("가상 스레드는 항상 데몬 스레드이며, 우선순위는 NORM_PRIORITY로 고정된다") {
        val thread = Thread.ofVirtual().unstarted { }
        thread.priority = Thread.MAX_PRIORITY // 설정해도 무시됨

        thread.isDaemon.shouldBeTrue()
        thread.priority shouldBe Thread.NORM_PRIORITY
    }

    test("가상 스레드는 interrupt를 지원한다") {
        var wasInterrupted = false

        val thread = Thread.startVirtualThread {
            try {
                Thread.sleep(10_000)
            } catch (e: InterruptedException) {
                wasInterrupted = true
            }
        }

        Thread.sleep(100)
        thread.interrupt()
        thread.join()

        wasInterrupted.shouldBeTrue()
    }

    test("가상 스레드와 플랫폼 스레드는 같은 Thread API를 공유한다") {
        var virtualId = 0L
        var platformId = 0L

        val vThread = Thread.startVirtualThread { virtualId = Thread.currentThread().threadId() }
        val pThread = Thread.ofPlatform().start { platformId = Thread.currentThread().threadId() }

        vThread.join()
        pThread.join()

        (virtualId > 0).shouldBeTrue()
        (platformId > 0).shouldBeTrue()
    }
})
