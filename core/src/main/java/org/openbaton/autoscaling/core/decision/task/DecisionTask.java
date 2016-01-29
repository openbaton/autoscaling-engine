/*
 *
 *  * Copyright (c) 2015 Technische Universität Berlin
 *  *  Licensed under the Apache License, Version 2.0 (the "License");
 *  *  you may not use this file except in compliance with the License.
 *  *  You may obtain a copy of the License at
 *  *
 *  *         http://www.apache.org/licenses/LICENSE-2.0
 *  *
 *  *  Unless required by applicable law or agreed to in writing, software
 *  *  distributed under the License is distributed on an "AS IS" BASIS,
 *  *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  *  See the License for the specific language governing permissions and
 *  *  limitations under the License.
 *
 */

package org.openbaton.autoscaling.core.decision.task;

import org.openbaton.autoscaling.core.decision.DecisionEngine;
import org.openbaton.autoscaling.core.management.ActionMonitor;
import org.openbaton.catalogue.mano.common.AutoScalePolicy;
import org.openbaton.catalogue.mano.record.Status;
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

    private String nsr_id;

    private String vnfr_id;

    private AutoScalePolicy autoScalePolicy;

    private String name;

    private DecisionEngine decisionEngine;

    private ActionMonitor actionMonitor;

    public DecisionTask(String nsr_id, String vnfr_id, AutoScalePolicy autoScalePolicy, DecisionEngine decisionEngine, ActionMonitor actionMonitor) {
        this.nsr_id = nsr_id;
        this.vnfr_id = vnfr_id;
        this.autoScalePolicy = autoScalePolicy;
        this.decisionEngine = decisionEngine;
        this.actionMonitor = actionMonitor;
        this.name = "DecisionTask#" + nsr_id + ":" + vnfr_id;
    }


    @Override
    public void run() {
        log.debug("Requested Decision-making for AutoScalePolicy with id: " + autoScalePolicy.getId() + " of VNFR with id: " + vnfr_id + " of NSR with id: " + nsr_id);
        if (decisionEngine.getStatus(nsr_id, vnfr_id) == Status.ACTIVE) {
            log.debug("Status is ACTIVE. So send actions to ExecutionEngine");
            if (actionMonitor.isTerminating(vnfr_id)) {
                return;
            }
            decisionEngine.sendDecision(nsr_id, vnfr_id, autoScalePolicy.getActions(), autoScalePolicy.getCooldown());
            actionMonitor.finishedAction(vnfr_id);
        } else {
            log.debug("Status is not ACTIVE. So do not send actions to ExecutionEngine. Do nothing!");
        };
    }
}
