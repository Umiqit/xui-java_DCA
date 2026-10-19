package site.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import site.entity.TicketMessage;

import java.util.List;

public interface TicketMessageRepository extends JpaRepository<TicketMessage, Long> {

    List<TicketMessage> findByTicketIdOrderByCreatedAtAsc(Long ticketId);

    long countByTicketIdAndSenderAndReadByUserFalse(Long ticketId, String sender);

    long countByTicketIdAndSenderAndReadByAdminFalse(Long ticketId, String sender);

    @Modifying
    @Query("UPDATE TicketMessage m SET m.readByUser = true WHERE m.ticketId = :ticketId AND m.sender = :sender AND m.readByUser = false")
    int markReadByUser(@Param("ticketId") Long ticketId, @Param("sender") String sender);

    @Modifying
    @Query("UPDATE TicketMessage m SET m.readByAdmin = true WHERE m.ticketId = :ticketId AND m.sender = :sender AND m.readByAdmin = false")
    int markReadByAdmin(@Param("ticketId") Long ticketId, @Param("sender") String sender);
}
