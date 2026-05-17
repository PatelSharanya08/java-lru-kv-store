package com.redislite;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * RedisLite – Entry Point
 *
 * @EnableScheduling activates Spring's scheduling support so our TTL cleanup
 * job (annotated with @Scheduled) is picked up automatically.
 */
@SpringBootApplication
@EnableScheduling
public class RedisLiteApplication {

    public static void main(String[] args) {
        SpringApplication.run(RedisLiteApplication.class, args);
    }
}
