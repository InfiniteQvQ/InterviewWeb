package com.example.Interview.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // 映射 /api/images/** 到本地目录 ~/images/
        registry.addResourceHandler("/api/images/**")
                .addResourceLocations("file:" + System.getProperty("user.home") + "/images/");
    }
}