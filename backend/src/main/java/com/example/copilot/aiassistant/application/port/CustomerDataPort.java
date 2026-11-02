package com.example.copilot.aiassistant.application.port;

import com.example.copilot.aiassistant.domain.customer.CustomerRecord;
import com.example.copilot.aiassistant.domain.customer.c360.Customer360Record;
import com.example.copilot.aiassistant.domain.customer.c360.RealTimeStatusRecord;
import java.util.Optional;

public interface CustomerDataPort {

    /** Identity and scope fields (platform customer master). */
    Optional<CustomerRecord> findCustomer(String certNo);

    /** Platform getCustomer: the A-M card data. */
    Optional<Customer360Record> findCustomer360(String certNo);

    /** Platform getRealTimeCustomer: flags, profile photo and key alerts. */
    Optional<RealTimeStatusRecord> findRealTimeStatus(String certNo);
}
