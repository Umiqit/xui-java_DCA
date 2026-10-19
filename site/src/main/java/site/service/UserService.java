package site.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import site.entity.User;
import site.repository.UserRepository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

@Service
public class UserService {

    private static final Logger log = LoggerFactory.getLogger(UserService.class);

    @Autowired
    private UserRepository userRepository;

    public Optional<User> findByTgId(long tgId) {
        return userRepository.findByTgId(tgId);
    }

    @Transactional
    public User findOrCreate(long tgId, String username, String fullName) {
        Optional<User> existing = userRepository.findByTgId(tgId);
        if (existing.isPresent()) {
            User user = existing.get();
            boolean changed = false;
            if (!Objects.equals(user.getUsername(), username)) {
                user.setUsername(username);
                changed = true;
            }
            if (!Objects.equals(user.getFullName(), fullName)) {
                user.setFullName(fullName);
                changed = true;
            }
            return changed ? userRepository.save(user) : user;
        }
        try {
            User user = new User();
            user.setTgId(tgId);
            user.setUsername(username);
            user.setFullName(fullName);
            user.setCreatedAt(Timestamp.from(Instant.now()));
            return userRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException e) {
            // Concurrent login from another device inserted the same tg_id first.
            log.debug("findOrCreate race for tgId={}, re-reading", tgId);
            return userRepository.findByTgId(tgId).orElseThrow(() -> e);
        }
    }
}
