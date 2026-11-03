package com.ludoarena.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ludoarena.dto.Dtos.ApiResponse;
import com.ludoarena.model.Enums.AccountStatus;
import com.ludoarena.model.User;
import com.ludoarena.repository.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Validates the Bearer access token and loads the user from the database on every request, so role changes
 * and suspensions take effect immediately (the token's role claim is informational only).
 * Not a Spring bean on purpose: it is registered only inside the security filter chain.
 */
public class JwtAuthFilter extends OncePerRequestFilter {
    private final JwtService jwtService;
    private final UserRepository users;
    private final ObjectMapper mapper;

    public JwtAuthFilter(JwtService jwtService, UserRepository users, ObjectMapper mapper) {
        this.jwtService = jwtService;
        this.users = users;
        this.mapper = mapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        String header = req.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            Optional<String> userId = jwtService.validateAndGetUserId(header.substring(7).trim());
            if (userId.isPresent()) {
                Optional<User> found = users.findById(userId.get());
                if (found.isPresent()) {
                    User u = found.get();
                    if (u.status == AccountStatus.SUSPENDED) {
                        res.setStatus(HttpServletResponse.SC_FORBIDDEN);
                        res.setContentType(MediaType.APPLICATION_JSON_VALUE);
                        mapper.writeValue(res.getOutputStream(),
                                ApiResponse.error("ACCOUNT_SUSPENDED", "Your account has been suspended", null));
                        return;
                    }
                    AuthUser principal = new AuthUser(u.id, u.email, u.name, u.role);
                    var auth = new UsernamePasswordAuthenticationToken(principal, null,
                            List.of(new SimpleGrantedAuthority("ROLE_" + u.role.name())));
                    SecurityContextHolder.getContext().setAuthentication(auth);
                }
            }
        }
        chain.doFilter(req, res);
    }
}
