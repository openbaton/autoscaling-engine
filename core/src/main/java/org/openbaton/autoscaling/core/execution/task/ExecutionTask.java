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

package org.openbaton.autoscaling.core.execution.task;

import org.openbaton.autoscaling.catalogue.Action;
import org.openbaton.autoscaling.core.execution.ExecutionEngine;
import org.openbaton.autoscaling.core.management.ActionMonitor;
import org.openbaton.catalogue.mano.common.ScalingAction;
import org.openbaton.catalogue.mano.record.Status;
import org.openbaton.catalogue.mano.record.VirtualNetworkFunctionRecord;
import org.openbaton.exceptions.NotFoundException;
import org.openbaton.exceptions.VimException;
import org.openbaton.sdk.api.exception.SDKException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Created by mpa on 27.10.15.
 */

@Service
@Scope("prototype")
public class ExecutionTask implements Runnable {

    protected Logger log = LoggerFactory.getLogger(this.getClass());

    private String nsr_id;

    private String vnfr_id;

    private Set<ScalingAction> actions;

    private String name;

    private ExecutionEngine executionEngine;

    private long cooldown;

    private ActionMonitor actionMonitor;

    public ExecutionTask(String nsr_id, String vnfr_id, Set<ScalingAction> actions, long cooldown, ExecutionEngine executionEngine, ActionMonitor actionMonitor) {
        this.actionMonitor = actionMonitor;
        log.debug("Initializing ExecutionTask for VNFR with id: " + vnfr_id + ". Actions: " + actions);
        this.nsr_id = nsr_id;
        this.vnfr_id = vnfr_id;
        this.actions = actions;
        this.cooldown = cooldown;
        this.executionEngine = executionEngine;
        this.name = "ExecutionTask#" + nsr_id + ":" + vnfr_id;
    }

    @Override
    public void run() {
        VirtualNetworkFunctionRecord vnfr = null;
        try {
            vnfr = executionEngine.updateVNFRStatus(nsr_id, vnfr_id, Status.SCALING);
        } catch (SDKException e) {
            log.error("Problems with SDK. Cannot update the VNFR. Scaling will not be executed");
            if (log.isDebugEnabled()) {
                log.error(e.getMessage(), e);
            }
            actionMonitor.finishedAction(vnfr_id);
            return;
        }
        try {
            for (ScalingAction action : actions) {
                switch (action.getType()) {
                    case SCALE_OUT:
                        vnfr = executionEngine.scaleOut(vnfr, Integer.parseInt(action.getValue()));
                        break;
                    case SCALE_OUT_TO:
                        executionEngine.scaleOutTo(vnfr, Integer.parseInt(action.getValue()));
                        break;
                    case SCALE_OUT_TO_FLAVOUR:
                        executionEngine.scaleOutToFlavour(vnfr, action.getValue());
                        break;
                    case SCALE_IN:
                        vnfr = executionEngine.scaleIn(vnfr, Integer.parseInt(action.getValue()));
                        break;
                    case SCALE_IN_TO:
                        executionEngine.scaleInTo(vnfr, Integer.parseInt(action.getValue()));
                        break;
                    case SCALE_IN_TO_FLAVOUR:
                        executionEngine.scaleInToFlavour(vnfr, action.getValue());
                        break;
                    default:
                        break;
                }
            }
        } catch (SDKException e) {
            log.error(e.getMessage(), e);
        } catch (NotFoundException e) {
            log.error(e.getMessage(), e);
        } catch (VimException e) {
            log.error(e.getMessage(), e);
        } finally {
            try {
                executionEngine.updateVNFRStatus(nsr_id, vnfr_id, Status.ACTIVE);
            } catch (SDKException e) {
                log.error("Problems with the SDK. Cannot Update VNFR. VNFR status remains in SCALE");
                if (log.isDebugEnabled()) {
                    log.error(e.getMessage(), e);
                }
                actionMonitor.finishedAction(vnfr_id);
            }
            if (actionMonitor.getAction(vnfr_id) == Action.SCALED) {
                log.info("[AUTOSCALING] Starting Cooldown " + new Date().getTime());
                executionEngine.startCooldown(nsr_id, vnfr_id, cooldown);
            } else {
                actionMonitor.finishedAction(vnfr_id);
            }
        }
        log.info("[AUTOSCALING] Executor executed Actions " + new Date().getTime());
    }
}
