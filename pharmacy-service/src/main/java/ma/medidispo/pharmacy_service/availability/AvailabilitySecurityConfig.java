package ma.medidispo.pharmacy_service.availability;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class AvailabilitySecurityConfig {

    @Bean
    @Order(1)
    SecurityFilterChain availabilityChain(HttpSecurity http) throws Exception {
        return http
                .securityMatcher("/api/availability-requests", "/api/availability-requests/**")
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.POST, "/api/availability-requests").permitAll()
                        .anyRequest().denyAll())
                .build();
    }

}
