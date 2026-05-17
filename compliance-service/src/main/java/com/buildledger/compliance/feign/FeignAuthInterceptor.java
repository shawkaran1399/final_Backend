package com.buildledger.compliance.feign;

import feign.RequestInterceptor;
import feign.RequestTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Component
public class FeignAuthInterceptor implements RequestInterceptor {

    @Override
    public void apply(RequestTemplate template) {
        var attrs = RequestContextHolder.getRequestAttributes();
        if (!(attrs instanceof ServletRequestAttributes servletAttrs)) return;
        var request = servletAttrs.getRequest();

        String auth = request.getHeader("Authorization");
        if (StringUtils.hasText(auth)) template.header("Authorization", auth);

        String username = request.getHeader("X-Username");
        String role = request.getHeader("X-User-Role");
        String userId = request.getHeader("X-User-Id");
        if (StringUtils.hasText(username)) template.header("X-Username", username);
        if (StringUtils.hasText(role))     template.header("X-User-Role", role);
        if (StringUtils.hasText(userId))   template.header("X-User-Id", userId);
    }
}
