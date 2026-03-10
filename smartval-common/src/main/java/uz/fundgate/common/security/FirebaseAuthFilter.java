package uz.fundgate.common.security;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseToken;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class FirebaseAuthFilter extends OncePerRequestFilter {

    @Value("${fundgate.internal-api-key:}")
    private String internalApiKey;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String path = request.getRequestURI();

        // Skip auth for health/actuator
        if (path.startsWith("/actuator") || path.equals("/health")) {
            filterChain.doFilter(request, response);
            return;
        }

        // Check internal API key
        String apiKey = request.getHeader("X-Internal-API-Key");
        if (apiKey != null && !apiKey.isEmpty() && apiKey.equals(internalApiKey)) {
            SecurityContextHolder.getContext().setAuthentication(
                    new UsernamePasswordAuthenticationToken("internal-service", null,
                            List.of(new SimpleGrantedAuthority("ROLE_INTERNAL"))));
            filterChain.doFilter(request, response);
            return;
        }

        // Check Bearer token
        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);
            try {
                FirebaseToken firebaseToken = FirebaseAuth.getInstance().verifyIdToken(token);
                String uid = firebaseToken.getUid();
                String email = firebaseToken.getEmail();

                UserContext userContext = UserContext.builder()
                        .uid(uid)
                        .email(email)
                        .name(firebaseToken.getName())
                        .build();

                UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                        userContext, null, List.of(new SimpleGrantedAuthority("ROLE_USER")));
                SecurityContextHolder.getContext().setAuthentication(auth);
                filterChain.doFilter(request, response);
                return;
            } catch (Exception e) {
                log.warn("Invalid Firebase token: {}", e.getMessage());
            }
        }

        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json");
        response.getWriter().write("{\"success\":false,\"message\":\"Unauthorized\"}");
    }
}
