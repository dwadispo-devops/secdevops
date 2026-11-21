package ma.medidispo.pharmacy_service.admin;

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
public class PharmacyAdminSecurityConfig {
    @Bean
    @Order(-3)
    SecurityFilterChain pharmacyAdminChain(HttpSecurity http, JwtService jwt) throws Exception {
        return http.securityMatcher(
                "/api/pharmacies/admin/pending",
                "/api/pharmacies/admin/*/validate"
            )
            .csrf(csrf -> csrf.disable())
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .exceptionHandling(exceptions -> exceptions
                .authenticationEntryPoint((request, response, error) -> response.sendError(401))
                .accessDeniedHandler((request, response, error) -> response.sendError(403)))
            .authorizeHttpRequests(authorize -> authorize.anyRequest().hasRole("ADMIN"))
            .addFilterBefore(new JwtAuthenticationFilter(jwt), UsernamePasswordAuthenticationFilter.class)
            .build();
    }
}
