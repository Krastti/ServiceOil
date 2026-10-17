package ru.krastti.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Учётная запись администратора: уникальный логин, BCrypt-хеш пароля и признак разрешённого входа. */
@Entity
@Table(name = "admin_account")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AdminEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(nullable = false, unique = true, length = 64)
    private String username;

    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;

    @Column(nullable = false)
    private boolean enabled;

    /** Меняет логин и, если передан, уже закодированный пароль. */
    public void changeCredentials(String username, String encodedPassword) {
        this.username = username;
        if (encodedPassword != null) this.passwordHash = encodedPassword;
    }
}
