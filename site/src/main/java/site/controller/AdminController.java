package site.controller;

import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import site.entity.Key;
import site.entity.Payment;
import site.entity.Server;
import site.entity.User;
import site.repository.KeyRepository;
import site.repository.PaymentRepository;
import site.repository.ServerRepository;
import site.repository.UserRepository;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Controller
@RequestMapping("${admin.panel-path}")
public class AdminController {

    private static final Logger log = LoggerFactory.getLogger(AdminController.class);
    private static final int PAGE_SIZE = 50;

    @Value("${telegram.admin-ids:}")
    private String adminIdsRaw;

    /** Parsed once at startup; a typo in ADMIN_IDS must not 500 every admin page. */
    private volatile Set<Long> adminIds;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private KeyRepository keyRepository;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private ServerRepository serverRepository;

    @Value("${admin.panel-path}")
    private String adminPath;

    @ModelAttribute("adminPath")
    public String getAdminPath() {
        return adminPath;
    }

    private Set<Long> adminIds() {
        Set<Long> ids = adminIds;
        if (ids == null) {
            synchronized (this) {
                ids = adminIds;
                if (ids == null) {
                    if (adminIdsRaw == null || adminIdsRaw.isBlank()) {
                        ids = Set.of();
                    } else {
                        try {
                            ids = Arrays.stream(adminIdsRaw.split(","))
                                    .map(String::trim)
                                    .filter(s -> !s.isEmpty())
                                    .map(Long::parseLong)
                                    .collect(Collectors.toSet());
                        } catch (NumberFormatException e) {
                            log.error("ADMIN_IDS contains a non-numeric value, treating as empty: {}", e.getMessage());
                            ids = Set.of();
                        }
                    }
                    adminIds = ids;
                }
            }
        }
        return ids;
    }

    private boolean isAdmin(HttpSession session) {
        Long tgId = (Long) session.getAttribute("tgId");
        return tgId != null && adminIds().contains(tgId);
    }

    @GetMapping
    public String admin(HttpSession session, Model model,
                        @RequestParam(defaultValue = "0") int page) {
        if (!isAdmin(session)) {
            return "redirect:/profile";
        }

        Pageable pageable = PageRequest.of(Math.max(0, page), PAGE_SIZE, Sort.by("id").descending());

        Page<User> usersPage = userRepository.findAll(pageable);
        Page<Key> keysPage = keyRepository.findAll(pageable);
        Page<Payment> paymentsPage = paymentRepository.findAll(pageable);

        model.addAttribute("users", usersPage.getContent());
        model.addAttribute("keys", keysPage.getContent());
        model.addAttribute("payments", paymentsPage.getContent());
        model.addAttribute("servers", serverRepository.findAll());

        model.addAttribute("usersPage", usersPage.getNumber());
        model.addAttribute("usersTotalPages", Math.max(1, usersPage.getTotalPages()));
        model.addAttribute("keysPage", keysPage.getNumber());
        model.addAttribute("keysTotalPages", Math.max(1, keysPage.getTotalPages()));
        model.addAttribute("paymentsPage", paymentsPage.getNumber());
        model.addAttribute("paymentsTotalPages", Math.max(1, paymentsPage.getTotalPages()));
        return "admin";
    }

    @PostMapping("/add-server")
    public String addServer(HttpSession session, RedirectAttributes ra,
                            @RequestParam String name,
                            @RequestParam String location,
                            @RequestParam String url,
                            @RequestParam String username,
                            @RequestParam String password,
                            @RequestParam(required = false) String certPath,
                            @RequestParam(defaultValue = "true") boolean active,
                            @RequestParam(defaultValue = "1") int weight) {
        if (!isAdmin(session)) {
            return "redirect:/profile";
        }
        try {
            Server s = new Server();
            s.setName(name);
            s.setLocation(location);
            s.setUrl(url);
            s.setUsername(username);
            s.setPassword(password);
            s.setCertPath(certPath);
            s.setActive(active);
            s.setWeight(weight);
            serverRepository.save(s);
            ra.addFlashAttribute("message", "Сервер добавлен!");
        } catch (Exception e) {
            log.error("Failed to add server", e);
            ra.addFlashAttribute("error", "Ошибка добавления сервера.");
        }
        return "redirect:" + adminPath;
    }

    @PostMapping("/del-server")
    public String delServer(HttpSession session, RedirectAttributes ra, @RequestParam Long id) {
        if (!isAdmin(session)) {
            return "redirect:/profile";
        }
        try {
            serverRepository.deleteById(id);
            ra.addFlashAttribute("message", "Сервер удалён.");
        } catch (Exception e) {
            log.error("Failed to delete server {}", id, e);
            ra.addFlashAttribute("error", "Ошибка удаления сервера.");
        }
        return "redirect:" + adminPath;
    }
}
