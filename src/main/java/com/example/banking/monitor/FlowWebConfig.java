package com.example.banking.monitor;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** Registers the controller-layer flow interceptor for API traffic. */
@Configuration
public class FlowWebConfig implements WebMvcConfigurer {

    private final FlowInterceptor flowInterceptor;

    public FlowWebConfig(FlowInterceptor flowInterceptor) {
        this.flowInterceptor = flowInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(flowInterceptor).addPathPatterns("/api/**");
    }
}
