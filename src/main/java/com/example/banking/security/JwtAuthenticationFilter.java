package com.example.banking.security;

import com.example.banking.monitor.FlowContext;
import com.example.banking.monitor.FlowPublisher;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;
    private final CustomUserDetailsService userDetailsService;
    private final FlowPublisher flow;

    public JwtAuthenticationFilter(JwtUtil jwtUtil, CustomUserDetailsService userDetailsService, FlowPublisher flow) {
        this.jwtUtil = jwtUtil;
        this.userDetailsService = userDetailsService;
        this.flow = flow;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            String token = header.substring(7);
            try {
                if (jwtUtil.isValid(token) && SecurityContextHolder.getContext().getAuthentication() == null) {
                    String username = jwtUtil.extractUsername(token);
                    UserDetails userDetails = userDetailsService.loadUserByUsername(username);
                    UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                            userDetails, null, userDetails.getAuthorities());
                    auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(auth);
                    if (FlowContext.get() != null) {
                        FlowContext.get().setUser(username);
                        flow.auth("JWT authentication successful (Bearer ********)", true);
                    }
                }
            } catch (Exception ex) {
                // Invalid token -> leave unauthenticated; entry point handles 401.
                SecurityContextHolder.clearContext();
            }
        }
        chain.doFilter(request, response);
    }
}
