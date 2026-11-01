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

    /**
     * Stores the cluster service.
     *
     * @param cluster cluster service
     */
    public WebConfig(ClusterMembershipService cluster) {
        this.cluster = cluster;
    }

    /**
     * Adds the primary check to all game endpoints.
     *
     * @param registry Spring interceptor registry
     */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new HandlerInterceptor() {
            /**
             * Checks that this node is a ready primary before handling the request.
             *
             * @param request incoming request
             * @param response outgoing response
             * @param handler selected request handler
             * @return true when the request can proceed
             */
            @Override
            public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
                // Check the role before Spring parses request bodies or validates parameters.
                cluster.requirePrimary();
                return true;
            }
        }).addPathPatterns("/games/**");
    }
}
