package com.chikere.futureai.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

@Configuration(proxyBeanMethods = false)
public class WebSecurityConfiguration {

    @Bean
    SecurityFilterChain webSecurityFilterChain(HttpSecurity http) throws Exception {
        http.authorizeHttpRequests(authorize -> authorize
                .requestMatchers(HttpMethod.GET, "/professional-assessment", "/professional-assessment/*",
                        "/professional-assessment/*/report", "/professional-assessment/*/checkout/local",
                        "/professional-assessment/*/report.pdf",
                        "/css/**").permitAll()
                .requestMatchers(HttpMethod.POST, "/professional-assessment",
                        "/professional-assessment/*/checkout",
                        "/professional-assessment/*/checkout/local/confirm",
                        "/payments/stripe/webhook").permitAll()
                .anyRequest().authenticated())
                .csrf(csrf -> csrf.ignoringRequestMatchers("/payments/stripe/webhook"))
                .formLogin(Customizer.withDefaults());
        return http.build();
    }
}
