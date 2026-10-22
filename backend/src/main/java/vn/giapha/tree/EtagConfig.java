package vn.giapha.tree;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.filter.ShallowEtagHeaderFilter;

@Configuration
public class EtagConfig {
    @Bean
    FilterRegistrationBean<ShallowEtagHeaderFilter> graphEtag() {
        FilterRegistrationBean<ShallowEtagHeaderFilter> b = new FilterRegistrationBean<>(new ShallowEtagHeaderFilter());
        b.addUrlPatterns("/api/v1/clans/*", "/api/v1/persons/*");
        b.setName("graphEtag");
        return b;
    }
}
