package com.example.copilot.testsupport;

import com.example.copilot.mockplatform.customer.MockCustomer360Data;
import com.example.copilot.mockplatform.customer.MockCustomerData;
import com.example.copilot.mockplatform.customer.MockCustomerDataAdapter;
import tools.jackson.databind.json.JsonMapper;

/** Builds the mock customer data beans by hand for unit tests that run without Spring. */
public final class MockCustomerPorts {

    private MockCustomerPorts() {
    }

    public static MockCustomer360Data data360(MockCustomerData customers) {
        return new MockCustomer360Data(customers, JsonMapper.builder().build());
    }

    public static MockCustomerDataAdapter adapter() {
        MockCustomerData customers = new MockCustomerData();
        return new MockCustomerDataAdapter(customers, data360(customers));
    }
}
