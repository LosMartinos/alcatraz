package com.alcatraz;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class ServerApplication {

    /**
     * Starts the Spring server.
     *
     * @param args command-line arguments
     */
    public static void main(String[] args) {
        SpringApplication.run(ServerApplication.class, args);
    }
}
