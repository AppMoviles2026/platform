package com.collabtech.platform.identity.infrastructure.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

public final class BearerSessionFilter extends OncePerRequestFilter {
    private final DatabaseAccessTokenProvider tokens;
    public BearerSessionFilter(DatabaseAccessTokenProvider tokens) { this.tokens = tokens; }
    @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        String header = request.getHeader("Authorization");
        if (header != null) {
            if (!header.regionMatches(true, 0, "Bearer ", 0, 7)) { reject(response); return; }
            var actor = tokens.authenticate(header.substring(7));
            if (actor.isEmpty()) { reject(response); return; }
            var credentials = UsernamePasswordAuthenticationToken.authenticated(
                    actor.get().accountId(), null, List.of(new SimpleGrantedAuthority("ROLE_" + actor.get().role())));
            var context = SecurityContextHolder.createEmptyContext(); context.setAuthentication(credentials);
            SecurityContextHolder.setContext(context);
        }
        chain.doFilter(request, response);
    }
    static void reject(HttpServletResponse response) throws IOException {
        response.setStatus(401); response.setHeader("WWW-Authenticate", "Bearer");
        response.setContentType("application/json;charset=UTF-8"); response.setHeader("Cache-Control", "no-store");
        response.getWriter().write("{\"code\":\"UNAUTHORIZED\",\"message\":\"Se requiere una sesión válida.\",\"fieldErrors\":{}}");
    }
}
