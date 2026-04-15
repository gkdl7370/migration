package com.kdm.migration;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class MigrationApplication {
	public static void main(String[] args) {
		System.setProperty("user.timezone", "UTC");
		SpringApplication.run(MigrationApplication.class, args);
	}
}