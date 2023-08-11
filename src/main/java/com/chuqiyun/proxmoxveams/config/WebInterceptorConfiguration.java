package com.chuqiyun.proxmoxveams.config;

import com.chuqiyun.proxmoxveams.interceptor.LicenseCheckInterceptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
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
    @Value("${config.frontend_DomainName}")
    private String frontendDomainName;

    @Bean
    public LicenseCheckInterceptor requestHandlerInterceptor(){
        return new LicenseCheckInterceptor();
    }

    /**
     * 跨域配置
     * @param registry registry
     */
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        // 允许跨域访问的路径
        registry.addMapping("/**")
                //.allowedOrigins("http://127.0.0.1:8082")
                // 允许跨域访问的源
                .allowedOriginPatterns("*")
                // 允许请求方法
                .allowedMethods("POST", "GET", "PUT", "OPTIONS", "DELETE")
                // 预检间隔时间
                .maxAge(3600)
                // 允许头部设置
                .allowedHeaders("*")
                // 是否发送cookie
                .allowCredentials(true);
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // 拦截路径
        registry.addInterceptor(requestHandlerInterceptor()).addPathPatterns("/**")
                .excludePathPatterns("/"+ADMIN_PATH+"/loginDo")
                .excludePathPatterns("/user/loginDo")
                .excludePathPatterns("/user/registerDo")
                .excludePathPatterns("/doc.html")
                .excludePathPatterns("/swagger-resources/**", "/webjars/**", "/v2/**", "/swagger-ui.html/**")
                .excludePathPatterns("/error/**")
                .excludePathPatterns("/static/**")
                .excludePathPatterns("/favicon.ico")
                .excludePathPatterns("/swagger-ui/**");
    }
}
