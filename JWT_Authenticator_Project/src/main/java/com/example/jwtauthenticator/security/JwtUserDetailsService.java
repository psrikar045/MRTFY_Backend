package com.example.jwtauthenticator.security;

import com.example.jwtauthenticator.entity.User;
import com.example.jwtauthenticator.repository.UserRepository;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.Optional;

@Service
public class JwtUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;
    private final PasswordEncoder bcryptEncoder;

    public JwtUserDetailsService(UserRepository userRepository, PasswordEncoder bcryptEncoder) {
        this.userRepository = userRepository;
        this.bcryptEncoder = bcryptEncoder;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        throw new UnsupportedOperationException("Use loadUserByUsernameAndBrandId instead");
    }

    public UserDetails loadUserByUsernameAndBrandId(String username, String brandId) throws UsernameNotFoundException {
        // First try to find by username and brandId
        Optional<User> userOptional = userRepository.findByUsername(username);
        
        // If not found and we're using the default brand, try just by username
        if (!userOptional.isPresent() && "default".equals(brandId)) {
            userOptional = userRepository.findByUsername(username);
        }
        
        if (!userOptional.isPresent()) {
            throw new UsernameNotFoundException("User not found with username: " + username);
        }
        
        User user = userOptional.get();
        return new org.springframework.security.core.userdetails.User(user.getUsername(), user.getPassword(),
                Collections.singletonList(new SimpleGrantedAuthority("ROLE_" + user.getRole().name())));
    }
    public UserDetails loadUserByUsernameAndEmail(String username, String email) throws UsernameNotFoundException {
        // First try to find by username and brandId
        Optional<User> userOptional = userRepository.findByUsernameAndEmail(username, email);
        
        
        if (!userOptional.isPresent()) {
            throw new UsernameNotFoundException("User not found with username: " + username);
        }
        
        User user = userOptional.get();
        return new org.springframework.security.core.userdetails.User(user.getUsername(), user.getPassword(),
                Collections.singletonList(new SimpleGrantedAuthority("ROLE_" + user.getRole().name())));
    }

    public User save(User user) {
        user.setPassword(bcryptEncoder.encode(user.getPassword()));
        return userRepository.save(user);
    }
}
