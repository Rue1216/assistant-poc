package com.example.copilot.aiassistant.domain.tool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.copilot.TestCustomers;
import com.example.copilot.aiassistant.application.port.CustomerDataPort;
import com.example.copilot.aiassistant.domain.customer.CustomerRecord;
import com.example.copilot.aiassistant.domain.customer.c360.Customer360Record;
import com.example.copilot.aiassistant.domain.customer.c360.RealTimeStatusRecord;
import com.example.copilot.testsupport.MockCustomerPorts;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class TurnCustomerDataTest {

    private final AtomicInteger customer360Calls = new AtomicInteger();
    private final AtomicInteger realTimeCalls = new AtomicInteger();
    private final CustomerDataPort mock = MockCustomerPorts.adapter();

    private CustomerDataPort counting(boolean failing) {
        return new CustomerDataPort() {
            @Override
            public Optional<CustomerRecord> findCustomer(String certNo) {
                return mock.findCustomer(certNo);
            }

            @Override
            public Optional<Customer360Record> findCustomer360(String certNo) {
                customer360Calls.incrementAndGet();
                if (failing) {
                    throw new IllegalStateException("platform down");
                }
                return mock.findCustomer360(certNo);
            }

            @Override
            public Optional<RealTimeStatusRecord> findRealTimeStatus(String certNo) {
                realTimeCalls.incrementAndGet();
                if (failing) {
                    throw new IllegalStateException("platform down");
                }
                return mock.findRealTimeStatus(certNo);
            }
        };
    }

    @Test
    void customer360IsReadOnce() {
        TurnCustomerData data = turnData(counting(false), TestCustomers.RETIREE);

        Optional<Customer360Record> first = data.customer360();
        assertThat(first).isPresent();
        assertThat(data.customer360()).isSameAs(first);
        assertThat(data.customer360()).isSameAs(first);

        assertThat(customer360Calls).hasValue(1);
        assertThat(realTimeCalls).hasValue(0);
    }

    @Test
    void realTimeIsReadOnce() {
        TurnCustomerData data = turnData(counting(false), TestCustomers.RETIREE);

        Optional<RealTimeStatusRecord> first = data.realTime();
        assertThat(first).isPresent();
        assertThat(data.realTime()).isSameAs(first);
        assertThat(data.realTime()).isSameAs(first);

        assertThat(realTimeCalls).hasValue(1);
        assertThat(customer360Calls).hasValue(0);
    }

    @Test
    void anAbsentRecordIsRememberedToo() {
        TurnCustomerData data = turnData(counting(false), TestCustomers.UNKNOWN);

        assertThat(data.customer360()).isEmpty();
        assertThat(data.customer360()).isEmpty();

        assertThat(customer360Calls).hasValue(1);
    }

    @Test
    void aFailureIsRememberedAndRethrown() {
        TurnCustomerData data = turnData(counting(true), TestCustomers.RETIREE);

        Throwable first = catchFailure(data::customer360);
        Throwable second = catchFailure(data::customer360);

        assertThat(second).isSameAs(first);
        assertThat(customer360Calls).hasValue(1);
        Throwable realTimeFirst = catchFailure(data::realTime);
        assertThat(catchFailure(data::realTime)).isSameAs(realTimeFirst);
        assertThat(realTimeCalls).hasValue(1);
    }

    @Test
    void theTwoReadsAreIndependent() {
        TurnCustomerData data = turnData(counting(false), TestCustomers.RETIREE);

        data.realTime();
        assertThat(customer360Calls).hasValue(0);
        data.customer360();
        data.realTime();

        assertThat(customer360Calls).hasValue(1);
        assertThat(realTimeCalls).hasValue(1);
    }

    private static Throwable catchFailure(Runnable call) {
        Throwable[] thrown = new Throwable[1];
        assertThatThrownBy(call::run).isInstanceOf(IllegalStateException.class).satisfies(e -> thrown[0] = e);
        return thrown[0];
    }

    private static TurnCustomerData turnData(CustomerDataPort port, String certNo) {
        return new TurnCustomerData(() -> port.findCustomer360(certNo), () -> port.findRealTimeStatus(certNo));
    }
}
