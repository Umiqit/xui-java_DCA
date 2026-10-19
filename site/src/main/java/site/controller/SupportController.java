package site.controller;

import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import site.entity.Ticket;
import site.entity.TicketMessage;
import site.entity.User;
import site.repository.UserRepository;
import site.service.TicketService;

import java.util.List;
import java.util.Map;

@Controller
public class SupportController {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TicketService ticketService;

    @GetMapping("/support")
    public String support(HttpSession session, Model model) {
        Long userId = (Long) session.getAttribute("userId");
        if (userId == null) {
            return "redirect:/login";
        }

        User user = userRepository.findById(userId).orElse(null);
        if (user == null) {
            session.invalidate();
            return "redirect:/login";
        }

        model.addAttribute("user", user);
        return "support";
    }

    // ----- JSON API for the support page -----

    @GetMapping("/support/tickets")
    @ResponseBody
    public ResponseEntity<?> list(HttpSession session) {
        Long userId = (Long) session.getAttribute("userId");
        if (userId == null) return ResponseEntity.status(401).build();

        List<Map<String, Object>> tickets = ticketService.findByUser(userId).stream()
                .map(t -> Map.<String, Object>of(
                        "id", t.getId(),
                        "subject", t.getSubject(),
                        "status", uiStatus(t.getStatus()),
                        "createdAt", String.valueOf(t.getCreatedAt()),
                        "unread", ticketService.unreadByUser(t.getId())))
                .toList();
        return ResponseEntity.ok(tickets);
    }

    public record CreateTicketRequest(String subject, String message) {}

    @PostMapping("/support/tickets")
    @ResponseBody
    public ResponseEntity<?> create(HttpSession session, @RequestBody CreateTicketRequest req) {
        Long userId = (Long) session.getAttribute("userId");
        if (userId == null) return ResponseEntity.status(401).build();
        if (req.subject() == null || req.subject().isBlank() || req.message() == null || req.message().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Тема и описание обязательны"));
        }

        Ticket ticket = ticketService.create(userId, req.subject().trim(), req.message().trim());
        return ResponseEntity.ok(Map.of("id", ticket.getId()));
    }

    @GetMapping("/support/tickets/{id}")
    @ResponseBody
    public ResponseEntity<?> detail(HttpSession session, @PathVariable Long id) {
        Long userId = (Long) session.getAttribute("userId");
        if (userId == null) return ResponseEntity.status(401).build();

        Ticket ticket = ticketService.findOwnedTicket(id, userId);
        if (ticket == null) return ResponseEntity.status(404).build();

        ticketService.markReadByUser(id);
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
                "messages", messages));
    }

    public record AddMessageRequest(String body) {}

    @PostMapping("/support/tickets/{id}/messages")
    @ResponseBody
    public ResponseEntity<?> addMessage(HttpSession session, @PathVariable Long id,
                                        @RequestBody AddMessageRequest req) {
        Long userId = (Long) session.getAttribute("userId");
        if (userId == null) return ResponseEntity.status(401).build();

        Ticket ticket = ticketService.findOwnedTicket(id, userId);
        if (ticket == null) return ResponseEntity.status(404).build();
        if (Ticket.STATUS_CLOSED.equals(ticket.getStatus())) {
            return ResponseEntity.badRequest().body(Map.of("error", "Тикет закрыт"));
        }
        if (req.body() == null || req.body().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Пустое сообщение"));
        }

        ticketService.setStatus(id, Ticket.STATUS_OPEN);
        ticketService.addMessage(id, TicketMessage.SENDER_USER, req.body().trim());
        return ResponseEntity.ok(Map.of("success", true));
    }

    @PostMapping("/support/tickets/{id}/close")
    @ResponseBody
    public ResponseEntity<?> close(HttpSession session, @PathVariable Long id) {
        Long userId = (Long) session.getAttribute("userId");
        if (userId == null) return ResponseEntity.status(401).build();

        Ticket ticket = ticketService.findOwnedTicket(id, userId);
        if (ticket == null) return ResponseEntity.status(404).build();

        ticketService.setStatus(id, Ticket.STATUS_CLOSED);
        return ResponseEntity.ok(Map.of("success", true));
    }

    @PostMapping("/support/tickets/{id}/reopen")
    @ResponseBody
    public ResponseEntity<?> reopen(HttpSession session, @PathVariable Long id) {
        Long userId = (Long) session.getAttribute("userId");
        if (userId == null) return ResponseEntity.status(401).build();

        Ticket ticket = ticketService.findOwnedTicket(id, userId);
        if (ticket == null) return ResponseEntity.status(404).build();

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
}
