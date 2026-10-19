package site.entity;

import jakarta.persistence.*;
import java.sql.Timestamp;

@Entity
@Table(name = "ticket_messages")
public class TicketMessage {

    public static final String SENDER_USER = "USER";
    public static final String SENDER_ADMIN = "ADMIN";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "ticket_id", nullable = false)
    private Long ticketId;

    @Column(name = "sender", nullable = false, length = 10)
    private String sender;

    @Column(name = "body", nullable = false, columnDefinition = "TEXT")
    private String body;

    @Column(name = "created_at", nullable = false)
    private Timestamp createdAt;

    @Column(name = "read_by_user", nullable = false)
    private Boolean readByUser = false;

    @Column(name = "read_by_admin", nullable = false)
    private Boolean readByAdmin = false;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getTicketId() { return ticketId; }
    public void setTicketId(Long ticketId) { this.ticketId = ticketId; }

    public String getSender() { return sender; }
    public void setSender(String sender) { this.sender = sender; }

    public String getBody() { return body; }
    public void setBody(String body) { this.body = body; }

    public Timestamp getCreatedAt() { return createdAt; }
    public void setCreatedAt(Timestamp createdAt) { this.createdAt = createdAt; }

    public Boolean getReadByUser() { return readByUser; }
    public void setReadByUser(Boolean readByUser) { this.readByUser = readByUser; }

    public Boolean getReadByAdmin() { return readByAdmin; }
    public void setReadByAdmin(Boolean readByAdmin) { this.readByAdmin = readByAdmin; }
}
