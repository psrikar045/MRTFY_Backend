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
        settings.setProperty("auto_import", "spring.ftl as spring");
        settings.setProperty("template_exception_handler", "rethrow");
        configurer.setFreemarkerSettings(settings);
        
        return configurer;
    }
}