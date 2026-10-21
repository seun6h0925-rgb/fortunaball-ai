package com.fortunaball.admin.auth.config;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Set;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.util.matcher.RequestMatcher;

/**
 * 관리자 보안 설정 (세션 로그인 + 변조 요청 방지).
 *
 * 규칙
 *  1) /api/admin/auth/login 은 누구나 부를 수 있다. 그 밖의 /api/admin/** 는 로그인한 관리자(ROLE_ADMIN)만 쓴다.
 *  2) 관리자 API 중 데이터를 바꾸는 요청(POST·PATCH·PUT·DELETE)은 CSRF 토큰 헤더(X-XSRF-TOKEN)가 있어야 한다.
 *     (로그인 요청만 예외. 로그인 응답에서 토큰을 받는다)
 *  3) 일반 사용자 API 는 지금까지와 똑같이 동작한다. (관리자 로그인과 무관, CSRF 검사 없음: 비회원 토큰은 쿠키가 아니라 헤더로 보낸다)
 *
 * ⚠ 헷갈리기 쉬운 점
 *  - 스프링 보안을 넣으면 기본으로 모든 주소가 잠기고 로그인 화면이 생긴다. 그래서 여기서 그 기본 동작을 모두 끄고 위 규칙만 남긴다.
 *  - 아이디·비밀번호 확인은 스프링 기본 로그인 방식(formLogin)이 아니라 AdminAuthService 가 직접 한다. (JSON 로그인이라서)
 *  - UserDetailsService 빈은 "사용자 없음"으로 비워 둔다. 안 두면 서버를 켤 때마다 임시 비밀번호가 로그에 찍히는 기본 계정이 생긴다.
 *  - 쿠키 보안: 세션 쿠키는 HttpOnly·SameSite=Lax (application.yml). 실서버(https)에서는 secure=true 로 바꾼다. (배포 점검 항목)
 * 📚 배운 내용: @Configuration, @Bean, 스프링 시큐리티(학원에서는 세션/JWT 로그인으로 배움)
 */
@Configuration
@EnableWebSecurity
@EnableConfigurationProperties(AdminProperties.class)
public class SecurityConfig {

    static final String LOGIN_PATH = "/api/admin/auth/login";
    private static final Set<String> SAFE_METHODS = Set.of("GET", "HEAD", "OPTIONS", "TRACE");

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public UserDetailsService noDefaultUser() {
        return username -> {
            throw new UsernameNotFoundException("기본 사용자는 쓰지 않아요.");
        };
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, AdminSecurityHandlers handlers) throws Exception {
        http
                .csrf(csrf -> csrf
                        .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())   // 쿠키 XSRF-TOKEN, 헤더 X-XSRF-TOKEN
                        .csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler())      // 화면(React)이 쿠키 값을 그대로 헤더에 담는 방식
                        .requireCsrfProtectionMatcher(SecurityConfig::needsCsrfCheck))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(LOGIN_PATH).permitAll()
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")
                        .anyRequest().permitAll())
                .exceptionHandling(e -> e
                        .authenticationEntryPoint(handlers)
                        .accessDeniedHandler(handlers))
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .requestCache(AbstractHttpConfigurer::disable)
                .anonymous(Customizer.withDefaults());
        return http.build();
    }

    /** 관리자 API 중 데이터를 바꾸는 요청(로그인 제외)만 CSRF 검사를 한다. */
    static boolean needsCsrfCheck(HttpServletRequest request) {
        String uri = request.getRequestURI();
        return uri.startsWith("/api/admin/")
                && !uri.equals(LOGIN_PATH)
                && !SAFE_METHODS.contains(request.getMethod());
    }
}
