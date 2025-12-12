package com.aeye.app.deploy.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // Vue构建的静态资源
        registry.addResourceHandler("/assets/**")
                .addResourceLocations("classpath:/static/assets/");
        
        registry.addResourceHandler("/favicon.ico")
                .addResourceLocations("classpath:/static/");
        
        // 添加对Vue SPA的支持
        registry.addResourceHandler("/**")
                .addResourceLocations("classpath:/static/");
    }

    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        // 将所有前端路由指向index.html
        registry.addViewController("/").setViewName("forward:/index.html");
        registry.addViewController("/appBuild").setViewName("forward:/index.html");
        registry.addViewController("/appDeploy").setViewName("forward:/index.html");
        registry.addViewController("/logFiles").setViewName("forward:/index.html");
        registry.addViewController("/fileBrowser").setViewName("forward:/index.html");
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        // 配置CORS - 生产环境应限制允许的来源
        registry.addMapping("/**")
                .allowedOriginPatterns("http://localhost:*", "http://127.0.0.1:*")
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(true)
                .maxAge(3600);
    }

}
