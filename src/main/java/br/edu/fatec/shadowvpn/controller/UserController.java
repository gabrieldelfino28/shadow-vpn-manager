package br.edu.fatec.shadowvpn.controller;

import br.edu.fatec.shadowvpn.entity.User;
import br.edu.fatec.shadowvpn.entity.Certificate;
import br.edu.fatec.shadowvpn.service.UserService;
import br.edu.fatec.shadowvpn.service.CertificateService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Controller
@RequestMapping("/certificates")
public class UserController {

    @Autowired
    private CertificateService certificateService;

    @Autowired
    private UserService userService;

    @GetMapping
    public String listUserCertificates(Model model, Authentication auth,
                                       @RequestParam(value = "search", required = false) String search) {
        User user = userService.findByUsername(auth.getName());
        List<Certificate> certificates = (search == null || search.isEmpty()) ? certificateService.findByUser(user)
                : certificateService.searchByLabel(user, search);
        model.addAttribute("certificates", certificates);
        model.addAttribute("user", user);
        return "certificate/list";
    }

    @PostMapping("/{certificateId}/revoke")
    public String revokeUserCertificate(@PathVariable Long certificateId, Authentication auth) {
        User user = userService.findByUsername(auth.getName());
        if (certificateService.isFromThisUser(certificateId, user)) {
            certificateService.revoke(certificateId);
        }
        return "redirect:/certificates";
    }

    @GetMapping("/create")
    public String createUserVpn(Authentication auth) {
        User user = userService.findByUsername(auth.getName());
        certificateService.create(user.getId());
        return "redirect:/certificates";
    }
}
