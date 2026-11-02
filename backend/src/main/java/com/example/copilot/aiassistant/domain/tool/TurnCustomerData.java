package com.example.copilot.aiassistant.domain.tool;

import com.example.copilot.aiassistant.domain.customer.c360.Customer360Record;
import com.example.copilot.aiassistant.domain.customer.c360.RealTimeStatusRecord;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * The platform customer data of one question: each platform API is read at most once, on first use, and the result
 * (or the exception) is remembered. Shared by the customer tools and the customer-notes prompt section. One instance
 * per question; never kept across questions.
 */
public final class TurnCustomerData {

    private final Lazy<Customer360Record> customer360;
    private final Lazy<RealTimeStatusRecord> realTime;

    public TurnCustomerData(Supplier<Optional<Customer360Record>> customer360,
                            Supplier<Optional<RealTimeStatusRecord>> realTime) {
        this.customer360 = new Lazy<>(customer360);
        this.realTime = new Lazy<>(realTime);
    }

    public Optional<Customer360Record> customer360() {
        return customer360.get();
    }

    public Optional<RealTimeStatusRecord> realTime() {
        return realTime.get();
    }

    /** Loads once; a failure is remembered and rethrown as the same exception on every later call. */
    private static final class Lazy<T> {

        private final Supplier<Optional<T>> loader;
        private boolean loaded;
        private Optional<T> value;
        private RuntimeException failure;

        Lazy(Supplier<Optional<T>> loader) {
            this.loader = loader;
        }

        synchronized Optional<T> get() {
            if (!loaded) {
                loaded = true;
                try {
                    value = loader.get();
                } catch (RuntimeException e) {
                    failure = e;
                }
            }
            if (failure != null) {
                throw failure;
            }
            return value;
        }
    }
}
