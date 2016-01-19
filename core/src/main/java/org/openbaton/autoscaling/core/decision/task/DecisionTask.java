package org.openbaton.autoscaling.core.decision.task;

import org.openbaton.autoscaling.core.decision.DecisionEngine;
import org.openbaton.catalogue.mano.common.AutoScalePolicy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;

import java.util.Properties;

/**
 * Created by mpa on 27.10.15.
 */

@Service
@Scope("prototype")
public class DecisionTask implements Runnable {

    protected Logger log = LoggerFactory.getLogger(this.getClass());

    private Properties properties;

    private String nsr_id;

    private String vnfr_id;

    private AutoScalePolicy autoScalePolicy;

    private String name;

    private DecisionEngine decisionEngine;

    public DecisionTask(String nsr_id, String vnfr_id, AutoScalePolicy autoScalePolicy, Properties properties, DecisionEngine decisionEngine) {
        this.properties = properties;
        this.nsr_id = nsr_id;
        this.vnfr_id = vnfr_id;
        this.autoScalePolicy = autoScalePolicy;
        this.decisionEngine = decisionEngine;
        this.name = "DecisionTask#" + nsr_id + ":" + vnfr_id;
    }


    @Override
    public void run() {
        if (decisionEngine.requestScaling(nsr_id, vnfr_id)) {
            decisionEngine.sendDecision(nsr_id, vnfr_id, autoScalePolicy.getActions(), autoScalePolicy.getCooldown());
        }
    }
}
