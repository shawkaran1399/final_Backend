package com.buildledger.contract.feign;

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
        if (attrs instanceof ServletRequestAttributes) {
            String auth = ((ServletRequestAttributes) attrs).getRequest().getHeader("Authorization");
            if (StringUtils.hasText(auth)) {
                template.header("Authorization", auth);
            }
        }
    }
}
