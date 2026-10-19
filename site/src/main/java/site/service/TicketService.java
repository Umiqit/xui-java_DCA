package site.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import site.entity.Ticket;
import site.entity.TicketMessage;
import site.repository.TicketMessageRepository;
import site.repository.TicketRepository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

@Service
public class TicketService {

    @Autowired
    private TicketRepository ticketRepository;

    @Autowired
    private TicketMessageRepository messageRepository;

    public List<Ticket> findByUser(Long userId) {
        return ticketRepository.findByUserIdOrderByUpdatedAtDesc(userId);
    }

    @Transactional
    public Ticket create(Long userId, String subject, String body) {
        Ticket ticket = new Ticket();
        ticket.setUserId(userId);
        ticket.setSubject(subject);
        ticket.setStatus(Ticket.STATUS_OPEN);
        Timestamp now = Timestamp.from(Instant.now());
        ticket.setCreatedAt(now);
        ticket.setUpdatedAt(now);
        ticket = ticketRepository.save(ticket);

        addMessage(ticket.getId(), TicketMessage.SENDER_USER, body);
        return ticket;
    }

    public Ticket findOwnedTicket(Long ticketId, Long userId) {
        Ticket ticket = ticketRepository.findById(ticketId).orElse(null);
        if (ticket == null || !ticket.getUserId().equals(userId)) return null;
        return ticket;
    }

    public Ticket findAny(Long ticketId) {
        return ticketRepository.findById(ticketId).orElse(null);
    }

    public List<TicketMessage> messages(Long ticketId) {
        return messageRepository.findByTicketIdOrderByCreatedAtAsc(ticketId);
    }

    @Transactional
    public TicketMessage addMessage(Long ticketId, String sender, String body) {
        TicketMessage msg = new TicketMessage();
        msg.setTicketId(ticketId);
        msg.setSender(sender);
        msg.setBody(body);
        msg.setCreatedAt(Timestamp.from(Instant.now()));
        msg.setReadByUser(TicketMessage.SENDER_USER.equals(sender));
        msg.setReadByAdmin(TicketMessage.SENDER_ADMIN.equals(sender));
        msg = messageRepository.save(msg);

        Ticket ticket = ticketRepository.findById(ticketId).orElseThrow();
        ticket.setUpdatedAt(Timestamp.from(Instant.now()));
        ticketRepository.save(ticket);
        return msg;
    }

    @Transactional
    public void setStatus(Long ticketId, String status) {
        Ticket ticket = ticketRepository.findById(ticketId).orElseThrow();
        ticket.setStatus(status);
        ticket.setUpdatedAt(Timestamp.from(Instant.now()));
        ticketRepository.save(ticket);
    }

    @Transactional
    public void markReadByUser(Long ticketId) {
        messageRepository.markReadByUser(ticketId, TicketMessage.SENDER_ADMIN);
    }

    @Transactional
    public void markReadByAdmin(Long ticketId) {
        messageRepository.markReadByAdmin(ticketId, TicketMessage.SENDER_USER);
    }

    public long unreadByUser(Long ticketId) {
        return messageRepository.countByTicketIdAndSenderAndReadByUserFalse(ticketId, TicketMessage.SENDER_ADMIN);
    }

    public long unreadByAdmin(Long ticketId) {
        return messageRepository.countByTicketIdAndSenderAndReadByAdminFalse(ticketId, TicketMessage.SENDER_USER);
    }
}
