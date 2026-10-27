package br.com.desenvolvedorgustavolopes.moneyPilot.notification;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "dismissed_alerts")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class DismissedAlert {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "alert_key", nullable = false)
    private String alertKey;

    @Column(name = "fingerprint", nullable = false)
    private String fingerprint;

    @Column(name = "dismissed_at", nullable = false)
    private Instant dismissedAt;
}
