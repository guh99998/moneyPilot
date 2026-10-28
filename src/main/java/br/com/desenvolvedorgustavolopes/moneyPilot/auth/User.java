package br.com.desenvolvedorgustavolopes.moneyPilot.auth;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "email", nullable = false, unique = true)
    private String email;

    /** Nulo em conta criada pelo Google que ainda não definiu senha. */
    @Column(name = "password_hash", length = 60)
    private String passwordHash;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "email_verified_at")
    private Instant emailVerifiedAt;

    @Column(name = "google_subject")
    private String googleSubject;

    @Column(name = "google_email")
    private String googleEmail;

    @Column(name = "avatar_url", length = 2048)
    private String avatarUrl;

    @Column(name = "avatar_hidden", nullable = false)
    private boolean avatarHidden;

    public boolean hasPassword() {
        return passwordHash != null;
    }
}
