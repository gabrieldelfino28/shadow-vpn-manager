package br.edu.fatec.shadowvpn.service;

import br.edu.fatec.shadowvpn.entity.Certificate;
import br.edu.fatec.shadowvpn.entity.User;
import br.edu.fatec.shadowvpn.repository.CertificateRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;

@Service
public class CertificateService {

    @Autowired
    private CertificateRepository certificateRepository;

    @Autowired
    @Lazy
    private UserService userService;

    private static final String CREATE_CERT_SCRIPT = "/home/usuario/createCert.sh";
    private static final String REVOKE_CERT_SCRIPT = "/home/usuario/revokeCert.sh";

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    public List<Certificate> findByUser(User user) {
        return certificateRepository.findByUser(user);
    }

    public List<Certificate> searchByLabel(User user, String label) {
        return certificateRepository.findByUserAndLabelContainingIgnoreCase(user, label);
    }

    public void create(Long userId) {

        User user = userService.findById(userId);
        String label = generateSecureEncodedLabel();
        String FILE_NAME = user.getUsername() + "_" + label;

        try {
//            Process p = pb.start();
//            BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()));
//            System.out.println("Executando como: " + r.readLine());

            Process process = new ProcessBuilder(
                    "sudo", CREATE_CERT_SCRIPT, FILE_NAME).start();
            process.waitFor();

            scriptLogger(process);

            Certificate certificate = new Certificate();
            certificate.setLabel(label);
            certificate.setCreatedDate(LocalDateTime.now());
            certificate.setDownloadUrl("/exports/download/" + user.getUsername() + "_" + certificate.getLabel() + ".zip");
            certificate.setUser(user);
            certificateRepository.save(certificate);

        } catch (IOException | InterruptedException e) {
            throw new RuntimeException("Failed to create certificate", e);
        }
    }

    public void revoke(Long certificateId) {
        try {
            Certificate certificate = certificateRepository.findById(certificateId).orElseThrow();
            String FILE_NAME = certificate.getUser().getUsername() + "_" + certificate.getLabel();

            Process process = new ProcessBuilder(
                    "sudo", REVOKE_CERT_SCRIPT, FILE_NAME).start();
            process.waitFor();

            scriptLogger(process);

            certificateRepository.deleteById(certificateId);
        } catch (IOException | InterruptedException e) {
            throw new RuntimeException("Failed to revoke certificate", e);
        }
    }

    public boolean isFromThisUser(Long certificateId, User user) {
        return certificateRepository.findById(certificateId)
                .map(certificate -> certificate.getUser().getId().equals(user.getId()))
                .orElse(false);
    }

    private String generateSecureEncodedLabel() {
        String label;
        byte[] bytes = new byte[5];
        do {
            SECURE_RANDOM.nextBytes(bytes);
            label = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        } while (certificateRepository.existsByLabel(label));
        return label;
    }

    private void scriptLogger(Process process) throws IOException{
        BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream())
        );
        String line;
        while ((line = reader.readLine()) != null) {
            System.out.println("[SCRIPT OUT] " + line);
        }

        BufferedReader errReader = new BufferedReader(
                new InputStreamReader(process.getErrorStream())
        );
        while ((line = errReader.readLine()) != null) {
            System.err.println("[SCRIPT ERR] " + line);
        }
    }
}
