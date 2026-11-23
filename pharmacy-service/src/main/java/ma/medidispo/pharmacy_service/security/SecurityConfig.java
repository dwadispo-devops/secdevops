package ma.medidispo.pharmacy_service.security;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            JwtService jwtService
    ) throws Exception {

        JwtAuthenticationFilter jwtFilter =
                new JwtAuthenticationFilter(jwtService);

        http
                .csrf(AbstractHttpConfigurer::disable)

                .httpBasic(AbstractHttpConfigurer::disable)

                .formLogin(AbstractHttpConfigurer::disable)

                .logout(AbstractHttpConfigurer::disable)

                .sessionManagement(session ->
                        session.sessionCreationPolicy(
                                SessionCreationPolicy.STATELESS
                        )
                )

                .exceptionHandling(exception -> exception
                        .authenticationEntryPoint((request, response, ex) ->
                                response.sendError(
                                        HttpServletResponse.SC_UNAUTHORIZED,
                                        "Authentification requise."
                                )
                        )
                        .accessDeniedHandler((request, response, ex) ->
                                response.sendError(
                                        HttpServletResponse.SC_FORBIDDEN,
                                        "Accès refusé."
                                )
                        )
                )

                .authorizeHttpRequests(auth -> auth
                        // TS07 : lecture de la documentation de l'API (coupée en production par SWAGGER_ENABLED=false)
                        .requestMatchers("/v3/api-docs", "/v3/api-docs/**", "/swagger-ui.html", "/swagger-ui/**").permitAll()
                        .requestMatchers("/actuator/health").permitAll()

                        // Page d'erreur interne de Spring : sans cette ligne,
                        // un 403 ou un 400 est transformé en 401.
                        .requestMatchers("/error").permitAll()

                        // Recherche des pharmacies accessible publiquement
                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/pharmacies/nearby"
                        ).permitAll()

                        // US15 : pharmacies de garde accessibles publiquement
                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/pharmacies/garde"
                        ).permitAll()

                        // Inscription d'une pharmacie réservée au pharmacien
                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/pharmacies"
                        ).hasRole("PHARMACIEN")

                        // US15 : le pharmacien gère les gardes de ses pharmacies
                        .requestMatchers("/api/pharmacies/me/gardes", "/api/pharmacies/me/gardes/**").hasRole("PHARMACIEN")

                        // US22 : alertes de retour en stock, réservées au patient
                        .requestMatchers("/api/alertes", "/api/alertes/**").hasRole("PATIENT")

                        // US03 : mes pharmacies
                        // US25 : tableau de bord réservé au pharmacien
                        .requestMatchers(HttpMethod.GET, "/api/pharmacies/me/dashboard").hasRole("PHARMACIEN")

                        .requestMatchers(HttpMethod.GET, "/api/pharmacies/me").hasRole("PHARMACIEN")

                        // Consultation par identifiant : utilisateur connecté
                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/pharmacies/*"
                        ).authenticated()

                        .anyRequest().authenticated()
                )

                .addFilterBefore(
                        jwtFilter,
                        UsernamePasswordAuthenticationFilter.class
                );

        return http.build();
    }
}
