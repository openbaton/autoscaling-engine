/*
 *
 *  *
 *  * Copyright (c) 2015 Technische Universität Berlin
 *  * Licensed under the Apache License, Version 2.0 (the "License");
 *  * you may not use this file except in compliance with the License.
 *  * You may obtain a copy of the License at
 *  *
 *  *       http://www.apache.org/licenses/LICENSE-2.0
 *  *
 *  * Unless required by applicable law or agreed to in writing, software
 *  * distributed under the License is distributed on an "AS IS" BASIS,
 *  * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  * See the License for the specific language governing permissions and
 *  * limitations under the License.
 *  *
 *
 */

package org.openbaton.autoscaling.core.decision.task;

import org.openbaton.autoscaling.core.decision.DecisionEngine;
import org.openbaton.autoscaling.core.management.ActionMonitor;
import org.openbaton.catalogue.mano.common.AutoScalePolicy;
import org.openbaton.catalogue.mano.common.ScalingAction;
import org.openbaton.catalogue.mano.common.ScalingActionType;
import org.openbaton.catalogue.mano.descriptor.VirtualDeploymentUnit;
import org.openbaton.catalogue.mano.record.Status;
import org.openbaton.catalogue.mano.record.VirtualNetworkFunctionRecord;
import org.openbaton.sdk.api.exception.SDKException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.Set;

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
        log.info("Requested decison-making for VNFR with id " + vnfr_id);
        log.debug("Requested Decision-making for AutoScalePolicy with id: " + autoScalePolicy.getId() + " of VNFR with id: " + vnfr_id + " of NSR with id: " + nsr_id);
        if (decisionEngine.getStatus(nsr_id, vnfr_id) == Status.ACTIVE) {
            log.debug("Status is ACTIVE. So continue with Decision-maikng. Next step is to check if scale-out or scale-in is possible based on numbers of already deployed VNFCInstances and limits");
            if (actionMonitor.isTerminating(vnfr_id)) {
                return;
            }
            try {
                VirtualNetworkFunctionRecord vnfr = decisionEngine.getVNFR(nsr_id, vnfr_id);
                Set<ScalingAction> filteredActions = new HashSet<>();
                for (ScalingAction action : autoScalePolicy.getActions()) {
                    log.debug("Decision-Maker checks if it possible to execute Action of type " + action.getType());
                    if (action.getType() == ScalingActionType.SCALE_OUT) {
                        for (VirtualDeploymentUnit vdu : vnfr.getVdu()) {
                            if (vdu.getVnfc_instance().size() < vdu.getScale_in_out()) {
                                log.debug("VDU with id " + vdu.getId() + " allows a scale-out. At least one more VNFCInstance is possible");
                                filteredActions.add(action);
                                break;
                            }
                            log.debug("VDU with id " + vdu.getId() + " reached already the maximum number of VNFCInstances. So no scale-out possible on this VDU.");
                        }
                    } else if (action.getType() == ScalingActionType.SCALE_IN) {
                        for (VirtualDeploymentUnit vdu : vnfr.getVdu()) {
                            if (vdu.getVnfc_instance().size() > 1) {
                                log.debug("VDU with id " + vdu.getId() + " allows a scale-in. At least one less VNFCInstance is possible");
                                filteredActions.add(action);
                                break;
                            }
                            log.debug("VDU with id " + vdu.getId() + " reached already the minimum number of VNFCInstances. So no scale-in possible on this VDU.");
                        }
                    }
                    if (filteredActions.contains(action)) {
                        log.info("Decision-Maker accepted the execution of ScalingAction " + action + " because conditions are met.");
                    } else {
                        log.info("Decision-Maker rejected the execution of ScalingAction " + action + " because conditions are not met.");
                    }
                }
                if (filteredActions.size() > 0) {
                    log.info("Send actions to ExecutionEngine -> " + filteredActions);
                    decisionEngine.sendDecision(nsr_id, vnfr_id, filteredActions, autoScalePolicy.getCooldown());
                }
            } catch (SDKException e) {
                log.warn("Not able to fetch the VNFR from the NFVO. Stop here and wait for the next request of decision-making....", e);
            } finally {
                actionMonitor.finishedAction(vnfr_id);
            }
        } else {
            log.debug("Status is not ACTIVE. So do not send actions to ExecutionEngine. Do nothing!");
        }
    }
}
