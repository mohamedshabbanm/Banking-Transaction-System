package com.ershak.service;

import com.ershak.db.Database;
import com.ershak.exception.AccountLockedException;
import com.ershak.exception.AuthenticationException;
import com.ershak.exception.NotFoundException;
import com.ershak.exception.ValidationException;
import com.ershak.model.Role;
import com.ershak.model.User;
import com.ershak.security.PasswordHasher;
import com.ershak.security.Session;
import com.ershak.security.SessionManager;
import com.ershak.util.BankClock;
import com.ershak.util.Validators;

/** Registration, login with lockout ("El talta tabta"), logout and password changes. */
public class AuthService {

    public record LoginResult(Session session, User user) {
    }

    private enum Outcome { SUCCESS, WRONG_PASSWORD, JUST_LOCKED, ALREADY_LOCKED, UNKNOWN_USER }

    private record Attempt(Outcome outcome, User user) {
    }

    private final Database db;
    private final Repositories repos;
    private final SettingsService settings;
    private final AuditService audit;
    private final NotificationService notifications;
    private final SessionManager sessions;

    public AuthService(Database db, Repositories repos, SettingsService settings, AuditService audit,
                       NotificationService notifications, SessionManager sessions) {
        this.db = db;
        this.repos = repos;
        this.settings = settings;
        this.audit = audit;
        this.notifications = notifications;
        this.sessions = sessions;
    }

    public User register(String fullName, String email, String phone, String username, String password) {
        String name = Validators.required(fullName, "Full name");
        String cleanEmail = Validators.email(email);
        String cleanPhone = Validators.phone(phone);
        String cleanUsername = Validators.username(username);
        Validators.password(password);

        return db.inTransaction(c -> {
            if (repos.users().existsByUsername(c, cleanUsername)) {
                throw new ValidationException("USERNAME_TAKEN", "That username is already taken. Try another.");
            }
            if (repos.users().existsByEmail(c, cleanEmail)) {
                throw new ValidationException("EMAIL_TAKEN", "An account with this email already exists.");
            }
            if (repos.users().existsByPhone(c, cleanPhone)) {
                throw new ValidationException("PHONE_TAKEN", "An account with this phone number already exists.");
            }
            User u = new User(name, cleanEmail, cleanPhone, cleanUsername, Role.CUSTOMER);
            setPassword(u, password);
            u.setCreatedAt(BankClock.nowMillis());
            repos.users().insert(c, u);
            audit.append(c, cleanUsername, "REGISTER", "New customer " + name);
            notifications.notify(c, u.getId(), "WELCOME", "Welcome to Ershak",
                    "Open your first account to start. حط القرش عالقرش، واحنا نحرسه.");
            return u;
        });
    }

    public LoginResult login(String username, String password) {
        if (username == null || username.isBlank() || password == null || password.isEmpty()) {
            throw new AuthenticationException("INVALID_CREDENTIALS", "Enter your username and password.");
        }
        String cleanUsername = username.trim().toLowerCase();
        BankSettings s = settings.current();

        // The failed-attempt counter must be saved even when the login fails,
        // so the decision is committed first and the error is thrown afterwards.
        Attempt attempt = db.inTransaction(c -> {
            User u = repos.users().findByUsername(c, cleanUsername).orElse(null);
            long now = BankClock.nowMillis();
            if (u == null) {
                audit.append(c, cleanUsername, "LOGIN_FAILED", "Unknown username");
                return new Attempt(Outcome.UNKNOWN_USER, null);
            }
            if (u.isLocked(now)) {
                return new Attempt(Outcome.ALREADY_LOCKED, u);
            }
            if (!PasswordHasher.verify(password, u.getSalt(), u.getPasswordHash())) {
                u.setFailedAttempts(u.getFailedAttempts() + 1);
                if (u.getFailedAttempts() >= s.maxFailedLogins()) {
                    u.setFailedAttempts(0);
                    u.setLockedUntil(now + s.lockMinutes() * 60_000L);
                    repos.users().updateLoginState(c, u);
                    audit.append(c, u.getUsername(), "USER_LOCKED",
                            s.maxFailedLogins() + " wrong passwords in a row");
                    notifications.notify(c, u.getId(), "LOCK", "التالتة تابتة",
                            "Someone typed your password wrong " + s.maxFailedLogins()
                                    + " times. We locked your login for " + s.lockMinutes() + " minutes.");
                    return new Attempt(Outcome.JUST_LOCKED, u);
                }
                repos.users().updateLoginState(c, u);
                audit.append(c, u.getUsername(), "LOGIN_FAILED", "Wrong password (" + u.getFailedAttempts() + ")");
                return new Attempt(Outcome.WRONG_PASSWORD, u);
            }
            u.setFailedAttempts(0);
            u.setLockedUntil(0);
            repos.users().updateLoginState(c, u);
            audit.append(c, u.getUsername(), "LOGIN", "Logged in");
            return new Attempt(Outcome.SUCCESS, u);
        });

        return switch (attempt.outcome()) {
            case SUCCESS -> new LoginResult(sessions.create(attempt.user()), attempt.user());
            case ALREADY_LOCKED, JUST_LOCKED -> throw new AccountLockedException(attempt.user().getLockedUntil());
            case WRONG_PASSWORD -> {
                int left = s.maxFailedLogins() - attempt.user().getFailedAttempts();
                throw new AuthenticationException("INVALID_CREDENTIALS",
                        "Wrong username or password. " + left + (left == 1 ? " try" : " tries") + " left.");
            }
            case UNKNOWN_USER -> throw new AuthenticationException("INVALID_CREDENTIALS",
                    "Wrong username or password.");
        };
    }

    public void logout(String token) {
        sessions.invalidate(token);
    }

    public User me(long userId) {
        return db.read(c -> repos.users().findById(c, userId)).orElseThrow(() -> new NotFoundException("User"));
    }

    public void changePassword(long userId, String currentPassword, String newPassword) {
        Validators.password(newPassword);
        db.inTransaction(c -> {
            User u = repos.users().findById(c, userId).orElseThrow(() -> new NotFoundException("User"));
            if (currentPassword == null || !PasswordHasher.verify(currentPassword, u.getSalt(), u.getPasswordHash())) {
                throw new ValidationException("WRONG_PASSWORD", "Your current password is not correct.");
            }
            if (currentPassword.equals(newPassword)) {
                throw new ValidationException("SAME_PASSWORD", "Choose a password different from the current one.");
            }
            setPassword(u, newPassword);
            u.setMustChangePassword(false);
            repos.users().updatePassword(c, u);
            audit.append(c, u.getUsername(), "PASSWORD_CHANGED", "Password changed");
            return null;
        });
    }

    static void setPassword(User u, String password) {
        String salt = PasswordHasher.newSalt();
        u.setSalt(salt);
        u.setPasswordHash(PasswordHasher.hash(password, salt));
    }
}
