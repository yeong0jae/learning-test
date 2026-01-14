package com.example.learningtest.compleatablefuture

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.string.shouldStartWith
import io.kotest.matchers.shouldBe
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.ThreadFactory
import java.util.concurrent.atomic.AtomicReference

class CompletableFutureNestedParallelismTest : FunSpec({

    test("비동기 스레드에서 여러 비동기 작업을 병렬로 실행할 수 있다.") {
        val parentExecutor: ExecutorService = Executors.newSingleThreadExecutor(namedThread("parent-"))
        val workerExecutor: ExecutorService = Executors.newFixedThreadPool(3, namedThread("worker-"))
        try {
            val parentThread = AtomicReference<String>()
            val results = CompletableFuture.supplyAsync({
                parentThread.set(Thread.currentThread().name)

                val futures = listOf(1, 2, 3).map { input ->
                    CompletableFuture.supplyAsync({ input * 2 }, workerExecutor)
                }

                futures.map { it.join() }
            }, parentExecutor).join()

            parentThread.get().shouldStartWith("parent-")
            results.toSet() shouldBe setOf(2, 4, 6)
        } finally {
            parentExecutor.shutdown()
            workerExecutor.shutdown()
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
