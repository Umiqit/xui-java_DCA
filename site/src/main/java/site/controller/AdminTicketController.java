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
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import site.entity.Ticket;
import site.entity.TicketMessage;
import site.entity.User;
import site.repository.TicketRepository;
import site.repository.UserRepository;
import site.service.TicketService;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Controller
@RequestMapping("${admin.panel-path}/tickets")
public class AdminTicketController {

    private static final Logger log = LoggerFactory.getLogger(AdminTicketController.class);
    private static final int PAGE_SIZE = 50;

    @Value("${telegram.admin-ids:}")
    private String adminIdsRaw;

    private volatile Set<Long> adminIds;

    @Autowired
    private TicketRepository ticketRepository;

    @Autowired
    private TicketService ticketService;

    @Autowired
    private UserRepository userRepository;

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
    public String ticketsPage(HttpSession session, Model model) {
        if (!isAdmin(session)) {
            return "redirect:/profile";
        }
        return "admin-tickets";
    }

    @GetMapping("/list")
    @ResponseBody
    public ResponseEntity<?> list(HttpSession session,
                                  @RequestParam(required = false) String status,
                                  @RequestParam(defaultValue = "0") int page) {
        if (!isAdmin(session)) return ResponseEntity.status(403).build();

        Pageable pageable = PageRequest.of(Math.max(0, page), PAGE_SIZE, Sort.by("updatedAt").descending());
        Page<Ticket> tickets = (status == null || status.isBlank())
                ? ticketRepository.findAll(pageable)
                : ticketRepository.findByStatus(dbStatus(status), pageable);

        List<Map<String, Object>> items = tickets.getContent().stream().map(t -> {
            User owner = userRepository.findById(t.getUserId()).orElse(null);
            return Map.<String, Object>of(
                    "id", t.getId(),
                    "subject", t.getSubject(),
                    "status", uiStatus(t.getStatus()),
                    "createdAt", String.valueOf(t.getCreatedAt()),
                    "updatedAt", String.valueOf(t.getUpdatedAt()),
                    "unread", ticketService.unreadByAdmin(t.getId()),
                    "user", owner != null
                            ? (owner.getTgId() + (owner.getUsername() != null ? " @" + owner.getUsername() : ""))
                            : String.valueOf(t.getUserId()));
        }).toList();

        return ResponseEntity.ok(Map.of(
                "items", items,
                "page", tickets.getNumber(),
                "totalPages", tickets.getTotalPages()));
    }

    @GetMapping("/{id}")
    @ResponseBody
    public ResponseEntity<?> detail(HttpSession session, @PathVariable Long id) {
        if (!isAdmin(session)) return ResponseEntity.status(403).build();

        Ticket ticket = ticketService.findAny(id);
        if (ticket == null) return ResponseEntity.status(404).build();

        ticketService.markReadByAdmin(id);
        User owner = userRepository.findById(ticket.getUserId()).orElse(null);
        List<Map<String, Object>> messages = ticketService.messages(id).stream()
                .map(m -> Map.<String, Object>of(
                        "id", m.getId(),
                        "sender", m.getSender(),
                        "body", m.getBody(),
                        "createdAt", String.valueOf(m.getCreatedAt())))
                .toList();
        return ResponseEntity.ok(Map.of(
                "id", ticket.getId(),
                "subject", ticket.getSubject(),
                "status", uiStatus(ticket.getStatus()),
                "user", owner != null
                        ? (owner.getTgId() + (owner.getUsername() != null ? " @" + owner.getUsername() : ""))
                        : String.valueOf(ticket.getUserId()),
                "messages", messages));
    }

    public record AddMessageRequest(String body) {}

    @PostMapping("/{id}/messages")
    @ResponseBody
    public ResponseEntity<?> addMessage(HttpSession session, @PathVariable Long id,
                                        @RequestBody AddMessageRequest req) {
        if (!isAdmin(session)) return ResponseEntity.status(403).build();

        Ticket ticket = ticketService.findAny(id);
        if (ticket == null) return ResponseEntity.status(404).build();
        if (Ticket.STATUS_CLOSED.equals(ticket.getStatus())) {
            return ResponseEntity.badRequest().body(Map.of("error", "Тикет закрыт"));
        }
        if (req.body() == null || req.body().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Пустое сообщение"));
        }

        ticketService.addMessage(id, TicketMessage.SENDER_ADMIN, req.body().trim());
        ticketService.setStatus(id, Ticket.STATUS_ANSWERED);
        return ResponseEntity.ok(Map.of("success", true));
    }

    @PostMapping("/{id}/take")
    @ResponseBody
    public ResponseEntity<?> take(HttpSession session, @PathVariable Long id) {
        if (!isAdmin(session)) return ResponseEntity.status(403).build();
        if (ticketService.findAny(id) == null) return ResponseEntity.status(404).build();

        ticketService.setStatus(id, Ticket.STATUS_ANSWERED);
        return ResponseEntity.ok(Map.of("success", true));
    }

    @PostMapping("/{id}/close")
    @ResponseBody
    public ResponseEntity<?> close(HttpSession session, @PathVariable Long id) {
        if (!isAdmin(session)) return ResponseEntity.status(403).build();
        if (ticketService.findAny(id) == null) return ResponseEntity.status(404).build();

        ticketService.setStatus(id, Ticket.STATUS_CLOSED);
        return ResponseEntity.ok(Map.of("success", true));
    }

    @PostMapping("/{id}/reopen")
    @ResponseBody
    public ResponseEntity<?> reopen(HttpSession session, @PathVariable Long id) {
        if (!isAdmin(session)) return ResponseEntity.status(403).build();
        if (ticketService.findAny(id) == null) return ResponseEntity.status(404).build();

        ticketService.setStatus(id, Ticket.STATUS_OPEN);
        return ResponseEntity.ok(Map.of("success", true));
    }

    private static String uiStatus(String status) {
        return switch (status) {
            case Ticket.STATUS_ANSWERED -> "progress";
            case Ticket.STATUS_CLOSED -> "closed";
            default -> "open";
        };
    }

    private static String dbStatus(String uiStatus) {
        return switch (uiStatus) {
            case "progress" -> Ticket.STATUS_ANSWERED;
            case "closed" -> Ticket.STATUS_CLOSED;
            default -> Ticket.STATUS_OPEN;
        };
    }
}
