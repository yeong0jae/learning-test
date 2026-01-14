package com.example.learningtest.compleatablefuture

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit

class CompletableFutureErrorHandlingTest : FunSpec({

    test("exceptionally는 예외가 발생했을 때 대체 값을 제공한다.") {
        val future = CompletableFuture
            .supplyAsync<String> { throw IllegalStateException("boom") }
            .exceptionally { "fallback" }

        future.join() shouldBe "fallback"
    }

    test("completeExceptionally는 예외로 완료시켜서 실패를 전파한다.") {
        val future = CompletableFuture<String>()
        future.completeExceptionally(RuntimeException("fail"))

        shouldThrow<RuntimeException> { future.join() }
    }

    test("orTimeout은 일정 시간이 지나면 예외로 완료하고 completeOnTimeout은 기본 값을 넣어준다.") {
        val timeoutFuture = CompletableFuture<String>()
        timeoutFuture.orTimeout(100, TimeUnit.MILLISECONDS)

        shouldThrow<ExecutionException> { timeoutFuture.get() }

        val defaultFuture = CompletableFuture<String>()
        val result = defaultFuture.completeOnTimeout("default", 100, TimeUnit.MILLISECONDS).get()

        result shouldBe "default"
    }
})
