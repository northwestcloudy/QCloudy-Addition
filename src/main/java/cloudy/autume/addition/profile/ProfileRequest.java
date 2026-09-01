package cloudy.autume.addition.profile;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

/** Cancellable request token carrying the generation used to reject late results. */
public final class ProfileRequest {
    private final long generation;
    private final CompletableFuture<ProfileLoadResult> future;
    private final Runnable cancelAction;
    private final AtomicBoolean cancelled = new AtomicBoolean();

    ProfileRequest(long generation,
                   CompletableFuture<ProfileLoadResult> future,
                   Runnable cancelAction) {
        this.generation = generation;
        this.future = Objects.requireNonNull(future, "future");
        this.cancelAction = Objects.requireNonNull(cancelAction, "cancelAction");
    }

    public long generation() {
        return generation;
    }

    public CompletableFuture<ProfileLoadResult> future() {
        return future;
    }

    public boolean cancel() {
        if (!cancelled.compareAndSet(false, true)) return false;
        cancelAction.run();
        return true;
    }
}
