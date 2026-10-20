package site.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpServletResponseWrapper;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Value("${admin.panel-path:/sys/dc-panel}")
    private String adminPath;

    @Value("${telegram.admin-ids:}")
    private String adminIdsRaw;

    /** Paths accessible without a session. */
    private static final List<String> PUBLIC_PATHS = List.of(
            "/", "/login", "/logout", "/auth/telegram", "/error",
            "/index.html", "/rail.html", "/favicon.ico",
            "/offer", "/offer.html",
            "/privacy", "/privacy.html", "/return-policy", "/return-policy.html",
            "/warranty", "/warranty.html", "/DreamCore.svg"
    );

    /** Static resource prefixes accessible without a session. */
    private static final List<String> PUBLIC_PREFIXES = List.of(
            "/static/**", "/css/**", "/js/**", "/images/**", "/icons/**", "/assets/**", "/favicon/**"
    );

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                // CSRF for everything; token exchanged via XSRF-TOKEN cookie / X-XSRF-TOKEN header,
                // and auto-injected as a hidden field into Thymeleaf forms with th:action.
                .csrf(csrf -> csrf
                        .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse()))
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED)
                        .sessionFixation(fixation -> fixation.migrateSession()))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(PUBLIC_PATHS.toArray(new String[0])).permitAll()
                        .requestMatchers(PUBLIC_PREFIXES.toArray(new String[0])).permitAll()
                        // Session authorization is enforced by SessionAuthFilter below;
                        // the SecurityContextHolder is not used for decisions.
                        .anyRequest().permitAll())
                // After CsrfFilter so that POST requests are CSRF-validated before
                // session authorization decisions are made.
                .addFilterAfter(new SessionAuthFilter(adminPath, parseAdminIds(adminIdsRaw)),
                        CsrfFilter.class);
        return http.build();
    }

    /**
     * Adds SameSite=Lax to every Set-Cookie header (JSESSIONID/SESSION, XSRF-TOKEN).
     * Registered ahead of the Spring Security filter chain so it wraps the whole chain.
     */
    @Bean
    public FilterRegistrationBean<SameSiteCookieFilter> sameSiteCookieFilter() {
        FilterRegistrationBean<SameSiteCookieFilter> registration =
                new FilterRegistrationBean<>(new SameSiteCookieFilter());
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return registration;
    }

    private static Set<Long> parseAdminIds(String raw) {
        if (raw == null || raw.isBlank()) {
            return Set.of();
        }
        return Arrays.stream(raw.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(Long::parseLong)
                .collect(Collectors.toSet());
    }

    /**
     * Enforces the project's session-based authorization model: any non-public path
     * requires the "userId" session attribute; the admin panel path additionally
     * requires the "tgId" session attribute to be in telegram.admin-ids.
     * Browser GET requests are redirected on failure, everything else gets 401/403.
     */
    static class SessionAuthFilter extends OncePerRequestFilter {

        private final String adminPath;
        private final Set<Long> adminIds;

        SessionAuthFilter(String adminPath, Set<Long> adminIds) {
            this.adminPath = adminPath;
            this.adminIds = adminIds;
        }

        @Override
        protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                        FilterChain filterChain) throws ServletException, IOException {
            String path = request.getRequestURI().substring(request.getContextPath().length());
            if (isPublic(path)) {
                filterChain.doFilter(request, response);
                return;
            }

            HttpSession session = request.getSession(false);
            Object userId = session != null ? session.getAttribute("userId") : null;
            if (userId == null) {
                reject(request, response, "/login", HttpServletResponse.SC_UNAUTHORIZED);
                return;
            }

            if (isAdminPath(path) && !isAdmin(session)) {
                reject(request, response, "/", HttpServletResponse.SC_FORBIDDEN);
                return;
            }

            Object tgId = session.getAttribute("tgId");
            if (tgId != null && SecurityContextHolder.getContext().getAuthentication() == null) {
                UsernamePasswordAuthenticationToken token = new UsernamePasswordAuthenticationToken(
                        tgId.toString(), null, AuthorityUtils.NO_AUTHORITIES);
                token.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(token);
            }
            try {
                filterChain.doFilter(request, response);
            } finally {
                SecurityContextHolder.clearContext();
            }
        }

        private boolean isPublic(String path) {
            if (PUBLIC_PATHS.contains(path)) {
                return true;
            }
            for (String prefix : PUBLIC_PREFIXES) {
                String prefixPath = prefix.substring(0, prefix.length() - "/**".length());
                if (path.startsWith(prefixPath + "/")) {
                    return true;
                }
            }
            return false;
        }

        private boolean isAdminPath(String path) {
            return path.equals(adminPath) || path.startsWith(adminPath + "/");
        }

        private boolean isAdmin(HttpSession session) {
            Object tgId = session.getAttribute("tgId");
            if (!(tgId instanceof Number number)) {
                return false;
            }
            return adminIds.contains(number.longValue());
        }

        private void reject(HttpServletRequest request, HttpServletResponse response,
                            String redirectPath, int status) throws IOException {
            if ("GET".equalsIgnoreCase(request.getMethod())) {
                response.sendRedirect(request.getContextPath() + redirectPath);
            } else {
                response.sendError(status);
            }
        }
    }

    /** Response wrapper that appends SameSite=Lax to Set-Cookie headers. */
    static class SameSiteCookieFilter extends OncePerRequestFilter {

        @Override
        protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                        FilterChain filterChain) throws ServletException, IOException {
            filterChain.doFilter(request, new SameSiteResponseWrapper(response));
        }
    }

    static class SameSiteResponseWrapper extends HttpServletResponseWrapper {

        SameSiteResponseWrapper(HttpServletResponse response) {
            super(response);
        }

        @Override
        public void setHeader(String name, String value) {
            if ("Set-Cookie".equalsIgnoreCase(name)) {
                value = withSameSiteLax(value);
            }
            super.setHeader(name, value);
        }

        @Override
        public void addHeader(String name, String value) {
            if ("Set-Cookie".equalsIgnoreCase(name)) {
                value = withSameSiteLax(value);
            }
            super.addHeader(name, value);
        }

        private static String withSameSiteLax(String value) {
            if (value == null || value.toLowerCase(Locale.ROOT).contains("samesite")) {
                return value;
            }
            return value + "; SameSite=Lax";
        }
    }
}
