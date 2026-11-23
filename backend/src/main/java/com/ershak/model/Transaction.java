package com.ershak.model;

import com.ershak.util.Money;

import java.math.BigDecimal;

/** One movement of money. Every transaction has a unique reference like TXN-20261123-000184. */
public class Transaction {

    private long id;
    private String reference;
    private TransactionType type;
    private Long fromAccountId;
    private Long toAccountId;
    private BigDecimal amount = Money.ZERO;
    private BigDecimal fee = Money.ZERO;
    private TransactionStatus status;
    private long initiatedBy;
    private String note;
    private long createdAt;
    private Long completedAt;

    public Transaction() {
    }

    public Transaction(TransactionType type, Long fromAccountId, Long toAccountId,
                       BigDecimal amount, BigDecimal fee, long initiatedBy) {
        this.type = type;
        this.fromAccountId = fromAccountId;
        this.toAccountId = toAccountId;
        this.amount = Money.of(amount);
        this.fee = Money.of(fee);
        this.initiatedBy = initiatedBy;
    }

    /** Amount plus fee: what actually leaves the sender's account. */
    public BigDecimal getTotalDebit() {
        return Money.of(amount.add(fee));
    }

    public long getId() { return id; }
    public void setId(long id) { this.id = id; }
    public String getReference() { return reference; }
    public void setReference(String reference) { this.reference = reference; }
    public TransactionType getType() { return type; }
    public void setType(TransactionType type) { this.type = type; }
    public Long getFromAccountId() { return fromAccountId; }
    public void setFromAccountId(Long fromAccountId) { this.fromAccountId = fromAccountId; }
    public Long getToAccountId() { return toAccountId; }
    public void setToAccountId(Long toAccountId) { this.toAccountId = toAccountId; }
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = Money.of(amount); }
    public BigDecimal getFee() { return fee; }
    public void setFee(BigDecimal fee) { this.fee = Money.of(fee); }
    public TransactionStatus getStatus() { return status; }
    public void setStatus(TransactionStatus status) { this.status = status; }
    public long getInitiatedBy() { return initiatedBy; }
    public void setInitiatedBy(long initiatedBy) { this.initiatedBy = initiatedBy; }
    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
    public long getCreatedAt() { return createdAt; }
    public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }
    public Long getCompletedAt() { return completedAt; }
    public void setCompletedAt(Long completedAt) { this.completedAt = completedAt; }
}
