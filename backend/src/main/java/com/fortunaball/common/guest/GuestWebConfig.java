package com.fortunaball.common.guest;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

/** GuestTokenArgumentResolver 를 스프링 MVC 에 등록한다. */
@Configuration
public class GuestWebConfig implements WebMvcConfigurer {

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(new GuestTokenArgumentResolver());
    }
}
