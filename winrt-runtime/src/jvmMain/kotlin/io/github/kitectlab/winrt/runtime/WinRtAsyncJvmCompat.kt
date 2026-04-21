package io.github.kitectlab.winrt.runtime

import java.util.concurrent.CompletableFuture

fun WinRtAsyncActionReference.toCompletableFuture(): CompletableFuture<Unit> {
    val future = CompletableFuture<Unit>()
    completeAsyncAction(
        currentStatus = status(),
        onCompleted = { future.complete(Unit) },
        onCancelled = {
            future.completeExceptionally(
                WinRtCancelledException("WinRT async action was canceled.", KnownHResults.ERROR_CANCELLED),
            )
        },
        onError = future::completeExceptionally,
    )
    if (future.isDone) {
        return future
    }

    val handle = whenCompleted { _, completedStatus ->
        completeAsyncAction(
            currentStatus = completedStatus,
            onCompleted = { future.complete(Unit) },
            onCancelled = {
                future.completeExceptionally(
                    WinRtCancelledException("WinRT async action was canceled.", KnownHResults.ERROR_CANCELLED),
                )
            },
            onError = future::completeExceptionally,
        )
    }

    future.whenComplete { _, _ ->
        if (future.isCancelled) {
            runCatching(::cancel)
        }
    }
    future.whenCompleteAsync { _, _ ->
        handle.close()
    }

    if (!future.isDone) {
        completeAsyncAction(
            currentStatus = status(),
            onCompleted = { future.complete(Unit) },
            onCancelled = {
                future.completeExceptionally(
                    WinRtCancelledException("WinRT async action was canceled.", KnownHResults.ERROR_CANCELLED),
                )
            },
            onError = future::completeExceptionally,
        )
    }

    return future
}

fun <T> WinRtAsyncOperationReference<T>.toCompletableFuture(): CompletableFuture<T> {
    val future = CompletableFuture<T>()
    completeAsyncOperation(
        currentStatus = status(),
        onCompleted = future::complete,
        onCancelled = {
            future.completeExceptionally(
                WinRtCancelledException("WinRT async operation was canceled.", KnownHResults.ERROR_CANCELLED),
            )
        },
        onError = future::completeExceptionally,
    )
    if (future.isDone) {
        return future
    }

    val handle = whenCompleted { _, completedStatus ->
        completeAsyncOperation(
            currentStatus = completedStatus,
            onCompleted = future::complete,
            onCancelled = {
                future.completeExceptionally(
                    WinRtCancelledException("WinRT async operation was canceled.", KnownHResults.ERROR_CANCELLED),
                )
            },
            onError = future::completeExceptionally,
        )
    }

    future.whenComplete { _, _ ->
        if (future.isCancelled) {
            runCatching(::cancel)
        }
    }
    future.whenCompleteAsync { _, _ ->
        handle.close()
    }

    if (!future.isDone) {
        completeAsyncOperation(
            currentStatus = status(),
            onCompleted = future::complete,
            onCancelled = {
                future.completeExceptionally(
                    WinRtCancelledException("WinRT async operation was canceled.", KnownHResults.ERROR_CANCELLED),
                )
            },
            onError = future::completeExceptionally,
        )
    }

    return future
}
