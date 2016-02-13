package org.openbaton.autoscaling;

import org.springframework.boot.SpringApplication;
import org.springframework.stereotype.Component;

/**
 * Created by mpa on 27.10.15.
 */
@Component
public class Starter {

    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }
}
