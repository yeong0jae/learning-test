package com.example.learningtest.compleatablefuture

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldStartWith
import io.kotest.matchers.types.shouldBeInstanceOf
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.ThreadFactory
import java.util.concurrent.atomic.AtomicReference

class CompletableFutureRunAsyncTest : FunSpec({

    test("runAsync는 반환값 없는 비동기 작업을 실행한다.") {
        val result = AtomicReference<String>()

        CompletableFuture.runAsync { result.set("done") }.join()

        result.get() shouldBe "done"
    }

    test("runAsync는 예외를 CompletionException으로 감싼다.") {
        val future = CompletableFuture.runAsync {
            throw IllegalStateException("boom")
        }

        val thrown = shouldThrow<RuntimeException> { future.join() }

        thrown::class.simpleName shouldBe "CompletionException"
        thrown.cause.shouldBeInstanceOf<IllegalStateException>()
    }

    test("runAsync는 지정한 Executor에서 실행된다.") {
        val worker: ExecutorService = Executors.newSingleThreadExecutor(namedThread("worker-"))
        try {
            val threadName = AtomicReference<String>()

            CompletableFuture.runAsync({ threadName.set(Thread.currentThread().name) }, worker).join()

            threadName.get().shouldStartWith("worker-")
        } finally {
            worker.shutdown()
        }
    }

    test("thenRunAsync는 별도 Executor에서 이어서 실행된다.") {
        val worker: ExecutorService = Executors.newSingleThreadExecutor(namedThread("worker-"))
        val continuation: ExecutorService = Executors.newSingleThreadExecutor(namedThread("continuation-"))
        try {
            val workerThread = AtomicReference<String>()
            val continuationThread = AtomicReference<String>()

            CompletableFuture
                .runAsync({ workerThread.set(Thread.currentThread().name) }, worker)
                .thenRunAsync({ continuationThread.set(Thread.currentThread().name) }, continuation)
                .join()

            workerThread.get().shouldStartWith("worker-")
            continuationThread.get().shouldStartWith("continuation-")
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
