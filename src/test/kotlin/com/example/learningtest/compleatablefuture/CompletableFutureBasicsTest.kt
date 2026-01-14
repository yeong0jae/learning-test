package com.example.learningtest.compleatablefuture

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldStartWith
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.ThreadFactory

class CompletableFutureBasicsTest : FunSpec({

    test("CompletableFuture는 별도 스레드에서 작업을 시작하고 join/get으로 결과를 회수할 수 있다.") {
        val future = CompletableFuture.supplyAsync { "hello" }

        val result = future.join()

        result shouldBe "hello"
        future.isDone.shouldBeTrue()
        future.isCompletedExceptionally.shouldBeFalse()
    }

    test("CompletableFuture 없이 Thread + join만 쓰면 결과 전달을 위해 별도 공유 공간이 필요하다.") {
        class Holder {
            var value: String? = null
        }
        val holder = Holder()

        val thread = Thread { holder.value = "hello" }
        thread.start()
        thread.join()

        holder.value shouldBe "hello"
    }

    test("get은 결과를 기다리지만 예외를 checked Exception(ExecutionException, InterruptedException)으로 감싼다.") {
        val future = CompletableFuture.supplyAsync { "hello" }

        val result = future.get()

        result shouldBe "hello"
    }

    test("join은 예외를 unchecked CompletionException으로 감싸 던진다.") {
        val future = CompletableFuture.supplyAsync<String> {
            throw IllegalStateException("boom")
        }

        val thrown = shouldThrow<RuntimeException> { future.join() }

        thrown::class.simpleName shouldBe "CompletionException"
        thrown.cause.shouldBeInstanceOf<IllegalStateException>()
    }

    test("thenApply는 이전 결과를 받아 동기적으로 변환한다.") {
        val future = CompletableFuture
            .supplyAsync { 10 }
            .thenApply { value -> value * 2 }

        val result = future.join()

        result shouldBe 20
    }

    test("thenApply는 이전 단계가 완료되는 스레드에서 이어서 실행된다.") {
        val worker: ExecutorService = Executors.newSingleThreadExecutor(namedThread("worker-"))
        try {
            val future = CompletableFuture
                .supplyAsync({ Thread.currentThread().name }, worker)
                .thenApply { name -> "$name->${Thread.currentThread().name}" }

            val threadChain = future.join()

            threadChain.shouldStartWith("worker-")
            threadChain.shouldContain("worker-")
        } finally {
            worker.shutdown()
        }
    }

    test("thenApplyAsync는 별도 비동기 작업으로 제출되며 지정한 Executor에서 실행된다.") {
        val worker: ExecutorService = Executors.newSingleThreadExecutor(namedThread("worker-"))
        val continuation: ExecutorService = Executors.newSingleThreadExecutor(namedThread("continuation-"))
        try {
            val future = CompletableFuture
                .supplyAsync({ Thread.currentThread().name }, worker)
                .thenApplyAsync({ name -> "$name->${Thread.currentThread().name}" }, continuation)

            val threadChain = future.join()

            threadChain.shouldStartWith("worker-")
            threadChain.shouldContain("continuation-")
        } finally {
            worker.shutdown()
            continuation.shutdown()
        }
    }
})

private fun namedThread(prefix: String): ThreadFactory {
    return ThreadFactory { runnable ->
        val thread = Thread(runnable)
        thread.name = prefix + thread.id
        thread
    }
}
