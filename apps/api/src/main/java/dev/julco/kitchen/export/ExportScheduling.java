package dev.julco.kitchen.export;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Turns on @Scheduled methods (cleanup of expired PDF links). */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
class ExportScheduling {}
