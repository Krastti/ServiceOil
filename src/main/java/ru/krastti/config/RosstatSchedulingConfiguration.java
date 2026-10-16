package ru.krastti.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Включает инфраструктуру расписания только по явной настройке. */
@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "rosstat.weekly.schedule.enabled", havingValue = "true")
public class RosstatSchedulingConfiguration {}
