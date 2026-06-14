package com.example.gateway_service.security;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.authentication.DelegatingAuthenticationEntryPoint;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import com.example.gateway_service.config.TestUiLoginKey;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.servlet.http.HttpSession;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

  @Value("${jwt.secret}")
  private String secretKey;

  @Bean
  public SecurityFilterChain securityFilterChain(
      HttpSecurity http,
      JwtTokenService jwtTokenService,
      ObjectMapper objectMapper) throws Exception {

    AuthenticationSuccessHandler successHandler = (request, response, authentication) -> {
      String token = jwtTokenService.generateToken(authentication);
      HttpSession session = request.getSession(false);
      boolean testUiLogin = session != null
          && Boolean.TRUE.equals(session.getAttribute(TestUiLoginKey.SESSION_FLAG.value()));
      if (testUiLogin && session != null) {
        session.removeAttribute(TestUiLoginKey.SESSION_FLAG.value());
        response.setStatus(HttpStatus.OK.value());
        response.setContentType(MediaType.TEXT_HTML_VALUE);
        response.getWriter().write(buildPopupSuccessHtml(token));
      } else {
        response.setStatus(HttpStatus.OK.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getWriter(), Map.of("accessToken", token));
      }
    };

    LinkedHashMap<RequestMatcher, AuthenticationEntryPoint> entryPoints = new LinkedHashMap<>();
    RequestMatcher apiMatcher = request -> {
      String uri = request.getRequestURI();
      if (uri == null) {
        return false;
      }
      return uri.startsWith("/project-manager-backend/api/");

    };
    entryPoints.put(apiMatcher, new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED));
    DelegatingAuthenticationEntryPoint delegatingEntryPoint = new DelegatingAuthenticationEntryPoint(entryPoints);
    delegatingEntryPoint.setDefaultEntryPoint(new LoginUrlAuthenticationEntryPoint("/login"));

    http
        .csrf(csrf -> csrf.disable())
        .cors(cors -> {
        })
        .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(auth -> auth
            .requestMatchers(HttpMethod.GET, "/login").permitAll()
            .requestMatchers("/oauth2/**").permitAll()
            .requestMatchers("/test-ui/**").permitAll()
            .requestMatchers("/actuator/**").permitAll()
            .requestMatchers("/error").permitAll()
            .requestMatchers(HttpMethod.GET, "/project-manager-backend/actuator/**").permitAll()
            .requestMatchers(HttpMethod.GET, "/project-manager-backend/api/v1/projects/**").permitAll()
            .requestMatchers(HttpMethod.GET, "/project-manager-backend/api/v1/status/**").permitAll()
            .requestMatchers(HttpMethod.GET, "/project-manager-backend/api/v1/tasks/**").permitAll()
            .requestMatchers(HttpMethod.GET, "/project-manager-backend/api/v1/images/**").permitAll()
            .requestMatchers("/project-manager-backend/api/v1/admin/**").hasAuthority("ADMIN")
            .requestMatchers("/project-manager-backend/api/v1/images/**").hasAuthority("ADMIN")
            .requestMatchers("/project-manager-backend/api/v1/projects/**").hasAuthority("ADMIN")
            .requestMatchers("/project-manager-backend/api/v1/tasks/**").hasAuthority("ADMIN")
            .requestMatchers("/project-manager-backend/api/v1/status/**").hasAuthority("ADMIN")

            .anyRequest().authenticated())
        .exceptionHandling(ex -> ex.authenticationEntryPoint(delegatingEntryPoint))
        .oauth2Login(oauth2 -> oauth2
            .loginPage("/login")
            .successHandler(successHandler))
        .oauth2ResourceServer(oauth2 -> oauth2
            .authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED))
            .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter())));

    return http.build();
  }

  @Bean
  public JwtAuthenticationConverter jwtAuthenticationConverter() {
    JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
    converter.setJwtGrantedAuthoritiesConverter(jwt -> {
      Object roles = jwt.getClaim("authorities");

      if (roles instanceof String rolesStr) {
        return List.of(rolesStr.split(","))
            .stream()
            .map(SimpleGrantedAuthority::new)
            .collect(Collectors.toList());
      } else if (roles instanceof Collection<?> rolesList) {
        return rolesList.stream()
            .map(Object::toString)
            .map(SimpleGrantedAuthority::new)
            .collect(Collectors.toList());
      }

      return List.of();
    });
    return converter;
  }

  @Bean
  public JwtDecoder jwtDecoder() {
    byte[] keyBytes = io.jsonwebtoken.io.Decoders.BASE64.decode(this.secretKey);
    SecretKey spec = new SecretKeySpec(keyBytes, "HmacSHA256");
    return NimbusJwtDecoder.withSecretKey(spec).build();
  }

  @Bean
  public CorsConfigurationSource corsConfigurationSource() {
    CorsConfiguration configuration = new CorsConfiguration();
    configuration.setAllowedOrigins(List.of("http://localhost:5173", "http://localhost:8080"));
    configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
    configuration.setAllowedHeaders(List.of("Authorization", "Content-Type", "Accept", "Origin"));
    configuration.setAllowCredentials(true);
    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/**", configuration);
    return source;
  }

  private String buildPopupSuccessHtml(String token) {
    String safeToken = token
        .replace("\\", "\\\\")
        .replace("'", "\\'")
        .replace("\"", "\\\"");
    return """
        <!DOCTYPE html>
        <html>
        <head><meta charset='UTF-8'><title>Login Success</title></head>
        <body>
        <script>
          (function () {
            const token = '%s';
            const storageKey = '%s';
            try {
              localStorage.setItem(storageKey, token);
              if (window.opener) {
                window.opener.postMessage({ token: token }, window.location.origin);
                window.close();
                return;
              }
            } catch (err) {
              console.error(err);
            }
            window.location.replace('/test-ui');
          })();
        </script>
        </body>
        </html>
        """.formatted(safeToken, TestUiLoginKey.STORAGE_KEY.value());
  }
}
