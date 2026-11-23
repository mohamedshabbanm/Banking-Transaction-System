package com.ershak.service;

import com.ershak.dao.AccountDao;
import com.ershak.dao.AuditDao;
import com.ershak.dao.FraudDao;
import com.ershak.dao.GoalDao;
import com.ershak.dao.NotificationDao;
import com.ershak.dao.OtpDao;
import com.ershak.dao.SettingsDao;
import com.ershak.dao.TransactionDao;
import com.ershak.dao.UserDao;

/** All DAOs in one place so services can be wired with a single object. */
public record Repositories(
        UserDao users,
        AccountDao accounts,
        TransactionDao transactions,
        FraudDao fraud,
        OtpDao otp,
        GoalDao goals,
        NotificationDao notifications,
        AuditDao audit,
        SettingsDao settings) {

    public static Repositories create() {
        return new Repositories(new UserDao(), new AccountDao(), new TransactionDao(), new FraudDao(),
                new OtpDao(), new GoalDao(), new NotificationDao(), new AuditDao(), new SettingsDao());
    }
}
