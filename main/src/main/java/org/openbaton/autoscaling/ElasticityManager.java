package org.openbaton.autoscaling;

import org.openbaton.autoscaling.core.ElasticityManagement;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Created by mpa on 27.10.15.
 */
@SpringBootApplication
public class ElasticityManager {
    @Autowired
    private ElasticityManagement elasticityManagement;

}
