package com.ershak.model;

/**
 * A person who can log in: a customer or a bank officer (admin).
 * Only the password hash and its salt are stored, never the password itself.
 */
public class User {

    private long id;
    private String fullName;
    private String email;
    private String phone;
    private String username;
    private String passwordHash;
    private String salt;
    private Role role;
    private int failedAttempts;
    private long lockedUntil;
    private boolean mustChangePassword;
    private long createdAt;

    public User() {
    }

    public User(String fullName, String email, String phone, String username, Role role) {
        this.fullName = fullName;
        this.email = email;
        this.phone = phone;
        this.username = username;
        this.role = role;
    }

    /** True while a lock from too many failed logins is still running. */
    public boolean isLocked(long nowMillis) {
        return lockedUntil > nowMillis;
    }

    public boolean isAdmin() {
        return role == Role.ADMIN;
    }

    public long getId() { return id; }
    public void setId(long id) { this.id = id; }
    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }
    public String getSalt() { return salt; }
    public void setSalt(String salt) { this.salt = salt; }
    public Role getRole() { return role; }
    public void setRole(Role role) { this.role = role; }
    public int getFailedAttempts() { return failedAttempts; }
    public void setFailedAttempts(int failedAttempts) { this.failedAttempts = failedAttempts; }
    public long getLockedUntil() { return lockedUntil; }
    public void setLockedUntil(long lockedUntil) { this.lockedUntil = lockedUntil; }
    public boolean isMustChangePassword() { return mustChangePassword; }
    public void setMustChangePassword(boolean mustChangePassword) { this.mustChangePassword = mustChangePassword; }
    public long getCreatedAt() { return createdAt; }
    public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }
}
