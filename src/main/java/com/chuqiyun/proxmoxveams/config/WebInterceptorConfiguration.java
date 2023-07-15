package com.chuqiyun.proxmoxveams.config;

import com.chuqiyun.proxmoxveams.interceptor.LicenseCheckInterceptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * @author mryunqi
 * @date 2023/4/19
 */

@Configuration
public class WebInterceptorConfiguration implements WebMvcConfigurer {
    @Value("${config.admin_path}")
    private String ADMIN_PATH;

    @Bean
    public LicenseCheckInterceptor requestHandlerInterceptor(){
        return new LicenseCheckInterceptor();
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // 拦截路径
        registry.addInterceptor(requestHandlerInterceptor()).addPathPatterns("/**");
        // 排除路径
        registry.addInterceptor(requestHandlerInterceptor()).excludePathPatterns("/"+ADMIN_PATH+"/loginDo");
        registry.addInterceptor(requestHandlerInterceptor()).excludePathPatterns("/user/loginDo");
        registry.addInterceptor(requestHandlerInterceptor()).excludePathPatterns("/user/registerDo");
        registry.addInterceptor(requestHandlerInterceptor()).excludePathPatterns("/doc.html");
        registry.addInterceptor(requestHandlerInterceptor()).excludePathPatterns("/swagger-resources/**", "/webjars/**", "/v2/**", "/swagger-ui.html/**");
        registry.addInterceptor(requestHandlerInterceptor()).excludePathPatterns("/error/**");
        registry.addInterceptor(requestHandlerInterceptor()).excludePathPatterns("/static/**");
        registry.addInterceptor(requestHandlerInterceptor()).excludePathPatterns("/favicon.ico");
        registry.addInterceptor(requestHandlerInterceptor()).excludePathPatterns("/swagger-ui/**");
    }
}
