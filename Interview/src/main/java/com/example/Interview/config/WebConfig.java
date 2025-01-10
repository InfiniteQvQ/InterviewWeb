package com.example.Interview.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig {
    @Bean
    public WebMvcConfigurer corsConfigurer() {
        return new WebMvcConfigurer() {
            @Override
            public void addCorsMappings(CorsRegistry registry) {
                registry.addMapping("/**") 
                        .allowedOrigins("http://localhost:3000", "http://18.117.101.227:3000, https://freecareer.org") 
                        .allowedMethods("GET", "POST", "PUT", "DELETE")
                        .allowCredentials(true) 
                        .allowedHeaders("*"); 
            }

            @Override
            public void addResourceHandlers(ResourceHandlerRegistry registry) {
                
                String imagePath = System.getProperty("user.home") + "/images/";
                registry.addResourceHandler("/images/**")
                        .addResourceLocations("file:" + imagePath);
            }
        };
    }
}
