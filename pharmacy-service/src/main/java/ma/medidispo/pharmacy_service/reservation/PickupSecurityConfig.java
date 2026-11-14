package ma.medidispo.pharmacy_service.reservation;

import ma.medidispo.pharmacy_service.security.JwtAuthenticationFilter;
import ma.medidispo.pharmacy_service.security.JwtService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
public class PickupSecurityConfig {

    @Bean
    @Order(-2)
    SecurityFilterChain pickupChain(HttpSecurity http, JwtService jwt) throws Exception {
        return http.securityMatcher("/api/pharmacies/*/reservations/**")
                .csrf(c -> c.disable())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(e -> e.authenticationEntryPoint((q, r, x) -> r.sendError(401))
                                         .accessDeniedHandler((q, r, x) -> r.sendError(403)))
                .authorizeHttpRequests(a -> a.anyRequest().hasRole("PHARMACIEN"))
                .addFilterBefore(new JwtAuthenticationFilter(jwt), UsernamePasswordAuthenticationFilter.class)
                .build();
    }
}
