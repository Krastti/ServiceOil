package ru.krastti.logging;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.slf4j.event.Level;

/** Задаёт безопасное имя операции и уровень записи её успешного завершения. */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface LogOperation {
    String value();

    /** Для INFO также записывается начало операции; подробности чтения остаются на DEBUG. */
    Level level() default Level.DEBUG;

    /** Повышает уровень успешного чтения до WARN при достижении настроенного порога. */
    boolean warnIfSlow() default false;
}
