package br.edu.fatec.shadowvpn.service;

import br.edu.fatec.shadowvpn.entity.User;
import br.edu.fatec.shadowvpn.entity.Certificate;
import br.edu.fatec.shadowvpn.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class UserService {

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CertificateService certificateService;

    public List<User> findAll() {
        return userRepository.findAll();
    }

    public List<User> searchByUsername(String username) {
        return userRepository.findByUsernameContainingIgnoreCase(username);
    }

    public User findByUsername(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("User " + username + " not found!"));
    }

    public User findById(Long id) {
        return userRepository.findById(id).orElseThrow();
    }

    public void save(User user) {
        user.setPassword(passwordEncoder.encode(user.getPassword()));
        userRepository.save(user);
    }

    public void update(User user) {
        User currentUser = userRepository.findById(user.getId()).orElseThrow();
        currentUser.setFirstName(user.getFirstName());
        currentUser.setLastName(user.getLastName());
        currentUser.setEmail(user.getEmail());
        if (user.getPassword() != null && !user.getPassword().isBlank()) {
            currentUser.setPassword(passwordEncoder.encode(user.getPassword()));
        }
        currentUser.setRole(user.getRole());
        userRepository.save(currentUser);
    }

    public void deleteById(Long id) {
        User user = findById(id);
        for (Certificate cert : user.getCertificates()) {
            certificateService.revoke(cert.getId());
        }
        userRepository.deleteById(id);
    }
}