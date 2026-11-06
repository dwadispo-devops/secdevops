package ma.medidispo.pharmacy_service.review;

import ma.medidispo.pharmacy_service.security.JwtAuthenticationFilter;
import ma.medidispo.pharmacy_service.security.JwtService;
import org.springframework.context.annotation.*;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
public class ReviewSecurityConfig {
    @Bean @Order(0)
    SecurityFilterChain reviewChain(HttpSecurity http, JwtService jwt) throws Exception {
        return http.securityMatcher("/api/pharmacies/me/requests", "/api/pharmacies/*/requests/**",
                                    "/api/availability-requests/*/prescription", "/api/availability-requests/*/responses")
            .csrf(c -> c.disable())
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .exceptionHandling(e -> e.authenticationEntryPoint((q,r,x) -> r.sendError(401))
                                     .accessDeniedHandler((q,r,x) -> r.sendError(403)))
            .authorizeHttpRequests(a -> a.requestMatchers("/api/availability-requests/**").hasRole("PATIENT")
                                        .anyRequest().hasRole("PHARMACIEN"))
            .addFilterBefore(new JwtAuthenticationFilter(jwt), UsernamePasswordAuthenticationFilter.class).build();
    }
}
