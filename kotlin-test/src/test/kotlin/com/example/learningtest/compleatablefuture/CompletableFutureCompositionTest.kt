package com.example.learningtest.compleatablefuture

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import java.util.concurrent.CompletableFuture

class CompletableFutureCompositionTest : FunSpec({

    test("thenCompose는 중첩된 CompletableFuture<CompletableFuture<T>>를 평탄화한다.") {
        val future = CompletableFuture
            .supplyAsync { 5 }
            .thenCompose { value -> CompletableFuture.supplyAsync { value + 7 } }

        val result = future.join()

        result shouldBe 12
    }

    test("thenApply에서 Future를 반환하면 결과 타입이 중첩된다.") {
        val nested = CompletableFuture
            .supplyAsync { 5 }
            .thenApply { value -> CompletableFuture.supplyAsync { value + 7 } }

        val innerResult = nested.join().join()

        innerResult shouldBe 12
    }

    test("thenCompose는 중첩 없이 바로 결과를 꺼낼 수 있다.") {
        val flattened = CompletableFuture
            .supplyAsync { 5 }
            .thenCompose { value -> CompletableFuture.supplyAsync { value + 7 } }

        val result = flattened.join()

        result shouldBe 12
    }

    test("thenCombine은 서로 독립적인 두 작업을 병렬로 수행하고 결과를 합친다.") {
        val left = CompletableFuture.supplyAsync { 3 }
        val right = CompletableFuture.supplyAsync { 4 }

        val combined = left.thenCombine(right) { l, r -> l + r }

        combined.join() shouldBe 7
    }

    test("allOf는 여러 Future가 모두 완료될 때까지 기다린다. 결과는 개별 Future에서 꺼낸다.") {
        val first = CompletableFuture.supplyAsync { "A" }
        val second = CompletableFuture.supplyAsync { "B" }
        val third = CompletableFuture.supplyAsync { "C" }

        val all = CompletableFuture.allOf(first, second, third)

        all.join()
        val results = listOf(first.join(), second.join(), third.join())

        results.shouldContainExactly("A", "B", "C")
    }

    test("allOf로 병렬 작업 완료를 기다리고, thenCompose로 다음 비동기 단계를 이어 붙인다.") {
        val first = CompletableFuture.supplyAsync { "A" }
        val second = CompletableFuture.supplyAsync { "B" }

        val combined = CompletableFuture
            .allOf(first, second)
            .thenCompose { CompletableFuture.supplyAsync { first.join() + second.join() } }

        combined.join() shouldBe "AB"
    }

    test("anyOf는 가장 먼저 완료되는 Future의 결과를 반환한다.") {
        val slow = CompletableFuture.supplyAsync {
            sleep(200)
            "slow"
        }
        val fast = CompletableFuture.supplyAsync { "fast" }

        val any = CompletableFuture.anyOf(slow, fast)

        any.join() shouldBe "fast"
    }
})

private fun sleep(millis: Long) {
    try {
        Thread.sleep(millis)
    } catch (ex: InterruptedException) {
        Thread.currentThread().interrupt()
    }
}
