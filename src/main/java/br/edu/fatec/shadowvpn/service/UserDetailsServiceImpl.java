package br.edu.fatec.shadowvpn.service;

import br.edu.fatec.shadowvpn.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.userdetails.*;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.Optional;

@Service
public class UserDetailsServiceImpl implements UserDetailsService {

    @Autowired
    private UserRepository userRepository;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {

        Optional<br.edu.fatec.shadowvpn.entity.User> optionalUser = userRepository.findByUsername(username);

        br.edu.fatec.shadowvpn.entity.User user = optionalUser.orElseThrow(() -> new UsernameNotFoundException("User " + username + " not found!!"));

        return new org.springframework.security.core.userdetails.User(user.getUsername(), user.getPassword(),
                Collections.singletonList(() -> user.getRole().name()));
    }
}