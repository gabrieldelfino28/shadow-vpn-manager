package br.edu.fatec.shadowvpn.controller;

import br.edu.fatec.shadowvpn.entity.User;
import br.edu.fatec.shadowvpn.entity.Certificate;
import br.edu.fatec.shadowvpn.service.UserService;
import br.edu.fatec.shadowvpn.service.CertificateService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Controller
@RequestMapping("/admin/users/{userId}/certificates")
public class CertificateManagerController {

    @Autowired
    private CertificateService certificateService;

    @Autowired
    private UserService userService;

    @GetMapping
    public String listCertificates(@PathVariable Long userId, @RequestParam(value = "search", required = false) String search,
                                   Model model) {
        User user = userService.findById(userId);
        List<Certificate> certificates = (search == null || search.isEmpty()) ? certificateService.findByUser(user)
                : certificateService.searchByLabel(user, search);
        model.addAttribute("user", user);
        model.addAttribute("certificates", certificates);
        return "vpn/list";
    }

    @GetMapping("/create")
    public String createCertificate(@PathVariable Long userId) {
        certificateService.create(userId);
        return "redirect:/admin/users/" + userId + "/certificates";
    }

    @PostMapping("/{certificateId}/revoke")
    public String revokeCertificate(@PathVariable Long userId, @PathVariable Long certificateId) {
        certificateService.revoke(certificateId);
        return "redirect:/admin/users/" + userId + "/certificates";
    }
}
