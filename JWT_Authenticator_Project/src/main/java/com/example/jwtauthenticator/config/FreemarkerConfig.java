package com.example.jwtauthenticator.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.view.freemarker.FreeMarkerConfigurer;
import org.springframework.beans.factory.annotation.Value;

import java.util.Properties;

@Configuration
public class FreemarkerConfig {

    @Value("${spring.freemarker.template-loader-path:classpath:/static/templates/}")
    private String templateLoaderPath;
    
    @Value("${spring.freemarker.charset:UTF-8}")
    private String charset;

    @Bean
    public FreeMarkerConfigurer freeMarkerConfigurer() {
        FreeMarkerConfigurer configurer = new FreeMarkerConfigurer();
        configurer.setTemplateLoaderPath(templateLoaderPath);
        configurer.setDefaultEncoding(charset);
        
        Properties settings = new Properties();
        // Removed spring.ftl auto-import as it doesn't exist and breaks template processing
        settings.setProperty("template_exception_handler", "debug");
        settings.setProperty("number_format", "computer");
        settings.setProperty("datetime_format", "iso");
        settings.setProperty("whitespace_stripping", "true");
        configurer.setFreemarkerSettings(settings);
        
        return configurer;
    }
}