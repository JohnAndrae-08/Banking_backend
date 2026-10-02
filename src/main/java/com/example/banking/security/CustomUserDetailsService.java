package com.example.banking.security;

import com.example.banking.model.User;
import com.example.banking.repository.UserRepository;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository users;

    public CustomUserDetailsService(UserRepository users) {
        this.users = users;
    }

    @Override
    public UserDetails loadUserByUsername(String usernameOrEmail) throws UsernameNotFoundException {
        User user = users.findByUsernameOrEmail(usernameOrEmail)
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));
        return toSpringUser(user);
    }

    public UserDetails loadByUserId(Long userId) {
        User user = users.findById(userId)
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));
        return toSpringUser(user);
    }

    private UserDetails toSpringUser(User user) {
        return new CustomUserPrincipal(
                user.getId(),
                user.getUsername(),
                user.getPasswordHash(),
                List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole())),
                user.getStatus());
    }

    /** Principal carrying the database user id for ownership checks. */
    public record CustomUserPrincipal(
            Long userId, String username, String password,
            List<SimpleGrantedAuthority> authorities, String status) implements UserDetails {

        @Override
        public java.util.Collection<? extends org.springframework.security.core.GrantedAuthority> getAuthorities() {
            return authorities;
        }

        @Override
        public String getPassword() {
            return password;
        }

        @Override
        public String getUsername() {
            return username;
        }

        @Override
        public boolean isAccountNonExpired() {
            return true;
        }

        @Override
        public boolean isAccountNonLocked() {
            return !"DISABLED".equalsIgnoreCase(status);
        }

        @Override
        public boolean isCredentialsNonExpired() {
            return true;
        }

        @Override
        public boolean isEnabled() {
            return !"DISABLED".equalsIgnoreCase(status);
        }
    }
}
