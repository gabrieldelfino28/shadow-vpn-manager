package br.edu.fatec.shadowvpn.repository;

import br.edu.fatec.shadowvpn.entity.User;
import br.edu.fatec.shadowvpn.entity.Certificate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CertificateRepository extends JpaRepository<Certificate, Long> {

    List<Certificate> findByUser(User user);

    boolean existsByLabel(String label);

    List<Certificate> findByUserAndLabelContainingIgnoreCase(User user, String label);
}
