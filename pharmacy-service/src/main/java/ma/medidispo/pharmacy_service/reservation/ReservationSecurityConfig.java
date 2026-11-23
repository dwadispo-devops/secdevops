
package ma.medidispo.pharmacy_service.reservation;

import ma.medidispo.pharmacy_service.security.JwtAuthenticationFilter;
import ma.medidispo.pharmacy_service.security.JwtService;
import org.springframework.context.annotation.*;
import org.springframework.core.annotation.Order;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/** Avant AvailabilitySecurityConfig (@Order(1)), qui refuse tout le reste de /api/availability-requests/**. */
@Configuration
@EnableScheduling
public class ReservationSecurityConfig {

    @Bean
    @Order(-1)
    SecurityFilterChain reservationChain(HttpSecurity http, JwtService jwt) throws Exception {
        return http.securityMatcher(
                "/api/availability-requests/*/reservations",
                "/api/availability-requests/*/reservation",
                "/api/availability-requests/*/reservations/*/rating",
                "/api/availability-requests/*/reservations/*/cancel"
            )
            .csrf(c -> c.disable())
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .exceptionHandling(e -> e
                    .authenticationEntryPoint((q, r, x) -> r.sendError(401))
                    .accessDeniedHandler((q, r, x) -> r.sendError(403)))
            .authorizeHttpRequests(a -> a.anyRequest().hasRole("PATIENT"))
            .addFilterBefore(
                    new JwtAuthenticationFilter(jwt),
                    UsernamePasswordAuthenticationFilter.class
            )
            .build();
    }
}
