package com.example.learningtest.virtualthread

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.longs.shouldBeLessThan
import io.kotest.matchers.shouldBe
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors
import kotlin.system.measureTimeMillis

/**
 * CompletableFuture와 가상 스레드의 조합을 학습한다.
 *
 * 핵심 포인트:
 * - CompletableFuture.supplyAsync에 Executor를 전달할 수 있다.
 * - newVirtualThreadPerTaskExecutor를 전달하면 기존 비동기 코드가 가상 스레드에서 돌아간다.
 * - 코드 변경 최소화: Executor 한 줄만 바꾸면 된다.
 */
class VirtualThreadWithCompletableFutureTest : FunSpec({

    test("CompletableFuture에 가상 스레드 Executor를 전달하면 가상 스레드에서 실행된다") {
        Executors.newVirtualThreadPerTaskExecutor().use { vtExecutor ->
            val future = CompletableFuture.supplyAsync({
                Thread.currentThread().isVirtual
            }, vtExecutor)

            future.join().shouldBeTrue()
        }
    }

    test("기존 fixedThreadPool Executor를 가상 스레드 Executor로 교체하면 한 줄만 바꾸면 된다") {
        // before: val executor = Executors.newFixedThreadPool(10)
        // after:
        val executor = Executors.newVirtualThreadPerTaskExecutor()

        executor.use {
            val future1 = CompletableFuture.supplyAsync({ "task-1" }, it)
            val future2 = CompletableFuture.supplyAsync({ "task-2" }, it)

            val combined = future1.thenCombine(future2) { a, b -> "$a+$b" }
            combined.join() shouldBe "task-1+task-2"
        }
    }

    test("CompletableFuture 체이닝도 가상 스레드 Executor에서 동작한다") {
        Executors.newVirtualThreadPerTaskExecutor().use { vtExecutor ->
            val result = CompletableFuture
                .supplyAsync({ 10 }, vtExecutor)
                .thenApplyAsync({ it * 2 }, vtExecutor)
                .thenApplyAsync({ it + 5 }, vtExecutor)
                .join()

            result shouldBe 25
        }
    }

    test("가상 스레드 Executor를 사용하면 I/O 병렬 처리에서 스레드 풀 크기 제약이 사라진다") {
        val taskCount = 100

        // 고정 풀은 풀 크기에 병목
        val fixedElapsed = measureTimeMillis {
            Executors.newFixedThreadPool(10).use { fixed ->
                val futures = (1..taskCount).map {
                    CompletableFuture.supplyAsync({
                        Thread.sleep(100)
                        it
                    }, fixed)
                }
                CompletableFuture.allOf(*futures.toTypedArray()).join()
            }
        }

        // 가상 스레드는 전부 동시 처리
        val virtualElapsed = measureTimeMillis {
            Executors.newVirtualThreadPerTaskExecutor().use { virtual ->
                val futures = (1..taskCount).map {
                    CompletableFuture.supplyAsync({
                        Thread.sleep(100)
                        it
                    }, virtual)
                }
                CompletableFuture.allOf(*futures.toTypedArray()).join()
            }
        }

        println("CompletableFuture + 고정풀(10): ${fixedElapsed}ms")
        println("CompletableFuture + 가상스레드: ${virtualElapsed}ms")

        virtualElapsed.shouldBeLessThan(500) // 전부 동시 처리
    }

    test("allOf + 가상 스레드로 여러 API 호출을 동시에 처리하는 패턴") {
        Executors.newVirtualThreadPerTaskExecutor().use { vtExecutor ->
            // 서로 다른 서비스에 동시에 요청하는 시나리오
            val userFuture = CompletableFuture.supplyAsync({
                Thread.sleep(100) // 사용자 서비스 호출
                "user-data"
            }, vtExecutor)

            val orderFuture = CompletableFuture.supplyAsync({
                Thread.sleep(150) // 주문 서비스 호출
                "order-data"
            }, vtExecutor)

            val productFuture = CompletableFuture.supplyAsync({
                Thread.sleep(80) // 상품 서비스 호출
                "product-data"
            }, vtExecutor)

            val elapsed = measureTimeMillis {
                CompletableFuture.allOf(userFuture, orderFuture, productFuture).join()
            }

            userFuture.join() shouldBe "user-data"
            orderFuture.join() shouldBe "order-data"
            productFuture.join() shouldBe "product-data"

            // 순차면 330ms, 병렬이면 약 150ms
            elapsed.shouldBeLessThan(300)
            println("3개 서비스 동시 호출: ${elapsed}ms (순차면 ~330ms)")
        }
    }
})
