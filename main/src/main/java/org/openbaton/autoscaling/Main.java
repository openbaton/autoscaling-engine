package org.openbaton.autoscaling;

import org.openbaton.autoscaling.utils.Utils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;

/**
 * Created by mpa on 27.10.15.
 */

public class Main {

    public static void main(String[] args) {

        SpringApplication.run(ElasticityManager.class, args);
    }

}
