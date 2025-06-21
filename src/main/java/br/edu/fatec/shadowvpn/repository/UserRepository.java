package br.edu.fatec.shadowvpn.repository;

import br.edu.fatec.shadowvpn.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    List<User> findByUsernameContainingIgnoreCase(String username);

    Optional<User> findByUsername(String username);
}
