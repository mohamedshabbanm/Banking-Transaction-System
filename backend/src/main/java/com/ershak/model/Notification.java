package com.ershak.model;

/** An in-app message: "5od balak". */
public record Notification(long id, long userId, String kind, String title, String message,
                           boolean read, long createdAt) {
}
