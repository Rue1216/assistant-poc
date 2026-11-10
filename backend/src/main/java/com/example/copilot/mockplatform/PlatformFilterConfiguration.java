package com.example.copilot.mockplatform;

import com.example.filter.TraceIdFilter;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/** Registers the platform filters this app does not component-scan; delete after moving onto the platform. */
@Configuration
@Import(TraceIdFilter.class)
public class PlatformFilterConfiguration {
}
