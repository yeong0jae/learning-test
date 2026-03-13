package com.example.learningtest.compleatablefuture

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import java.util.concurrent.CompletableFuture

class CompletableFutureLearningTasksTest : FunSpec({

    test("TODO: supplyAsync로 \"hello\"를 반환하는 Future를 만들어라.") {
        val future: CompletableFuture<String> = CompletableFuture.supplyAsync { "hello" }

        // TODO: thenApply를 사용해 길이를 구하는 Future로 변환하라.
        val lengthFuture: CompletableFuture<Int> = future.thenApply { it.length }

        // TODO: 결과를 join해서 5인지 검증하라.
        val result = lengthFuture.join()
        result shouldBe 5
    }

    test("TODO: thenApply를 사용하면 CompletableFuture<CompletableFuture<Int>>가 된다.") {
        val nested: CompletableFuture<CompletableFuture<Int>> = CompletableFuture.supplyAsync {
            CompletableFuture.supplyAsync { 2 }
        }

        // TODO: thenCompose를 사용하면 CompletableFuture<Int>로 평탄화된다.
        val flattened: CompletableFuture<Int> = nested.thenCompose { it }

        // TODO: nested의 결과는 2, flattened의 결과는 2가 되도록 구현하라.
        nested.join().join() shouldBe 2
        flattened.join() shouldBe 2
    }

    test("TODO: 예외가 발생하는 CompletableFuture를 만들어라.") {
        val failing: CompletableFuture<String> = CompletableFuture.supplyAsync { throw IllegalArgumentException("error") }

        // TODO: handle을 사용해 예외가 발생하면 "recovered"를 반환하라.
        val recovered: CompletableFuture<String> = failing.exceptionally { "recovered" }

        // TODO: recovered의 결과를 검증하라.
        recovered.join() shouldBe "recovered"
    }
})
