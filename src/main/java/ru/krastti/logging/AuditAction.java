package ru.krastti.logging;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Отмечает административное действие; значения заданы явно и не извлекаются из запроса. */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface AuditAction {
    Action value();

    enum Action {
        VEHICLE_TEMPLATE_CREATED,
        ADMIN_ACCOUNT_CHANGED
    }
}
