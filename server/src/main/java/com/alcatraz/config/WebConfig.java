package com.alcatraz.config;

import com.alcatraz.replication.ClusterMembershipService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {
    private final ClusterMembershipService cluster;

    public WebConfig(ClusterMembershipService cluster) {
        this.cluster = cluster;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new HandlerInterceptor() {
            @Override
            public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
                // Check the role before Spring parses request bodies or validates parameters.
                cluster.requirePrimary();
                return true;
            }
        }).addPathPatterns("/games/**");
    }
}
