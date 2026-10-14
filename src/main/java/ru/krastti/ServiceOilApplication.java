package ru.krastti;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class ServiceOilApplication {

    public static void main(String[] args) {
        var context = SpringApplication.run(ServiceOilApplication.class, args);
        if (context.getEnvironment().containsProperty("import.file")) context.close();
    }

}
