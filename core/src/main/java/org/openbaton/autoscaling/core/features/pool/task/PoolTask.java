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

package org.openbaton.autoscaling.core.features.pool.task;

import org.openbaton.autoscaling.catalogue.Action;
import org.openbaton.autoscaling.core.features.pool.PoolEngine;
import org.openbaton.autoscaling.core.management.ActionMonitor;
import org.openbaton.catalogue.mano.record.VNFCInstance;
import org.openbaton.exceptions.NotFoundException;
import org.openbaton.exceptions.VimException;
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
public class PoolTask implements Runnable {

    protected Logger log = LoggerFactory.getLogger(this.getClass());

    private PoolEngine poolEngine;

    private ActionMonitor actionMonitor;

    private String nsr_id;

    private int pool_size;

    private String name;

    public PoolTask(String nsr_id, int pool_size, PoolEngine poolEngine, ActionMonitor actionMonitor) throws NotFoundException {
        this.nsr_id = nsr_id;
        this.poolEngine = poolEngine;
        this.actionMonitor = actionMonitor;
        this.name = "PoolTask#" + nsr_id;
        this.pool_size = pool_size;
    }

    @Override
    public void run() {
        if ( !actionMonitor.requestAction(nsr_id, Action.DECIDE) ) {
            log.warn("Cannot reschedule PoolTask. Wrong state " + actionMonitor.getAction(nsr_id));
            return;
        }
        log.debug("Checking the pool of reserved VNFCInstances for NSR with id: " + nsr_id);
        Map<String, Map<String, Set<VNFCInstance>>> reservedInstances = poolEngine.getReservedInstances(nsr_id);
        log.debug("Currently reserved VNFCInstances: " + reservedInstances);
        for (String vnfr_id : reservedInstances.keySet()) {
            if (actionMonitor.isTerminating(nsr_id)) {
                log.debug("Terminated PoolTask gracefully");
                actionMonitor.finishedAction(nsr_id, Action.TERMINATED);
                return;
            }
            for (String vdu_id : reservedInstances.get(vnfr_id).keySet()) {
                if (actionMonitor.isTerminating(nsr_id)) {
                    log.debug("Terminated PoolTask gracefully");
                    actionMonitor.finishedAction(nsr_id, Action.TERMINATED);
                    return;
                }
                int launchPerRound = 3;
                int currentPoolSize = reservedInstances.get(vnfr_id).get(vdu_id).size();
                log.debug("Current pool size of NSR::VNFR::VDU: " + nsr_id + "::" + vnfr_id + "::" + vdu_id + " -> " + currentPoolSize);
                int launch = pool_size - currentPoolSize;
                while (launch > 0) {
                    Set<VNFCInstance> newReservedInstances = null;
                    int launchThisRound = 0;
                    if (launch >= launchPerRound) {
                        launchThisRound = launchPerRound;
                    } else {
                        launchThisRound = launch;
                    }
                    try {
                        log.debug("Launching " + launchThisRound + " VNFCInstances for the pool of NSR::VNFR::VDU: " + nsr_id + "::" + vnfr_id + "::" + vdu_id + " -> " + currentPoolSize);
                        newReservedInstances = poolEngine.allocateNewInstance(nsr_id, vnfr_id, vdu_id, launchThisRound);
                        launch = launch - launchThisRound;
                    } catch (NotFoundException e) {
                        log.error(e.getMessage(), e);
                        break;
                    }
                    reservedInstances.get(vnfr_id).get(vdu_id).addAll(newReservedInstances);
                    if (actionMonitor.isTerminating(nsr_id)) {
                        log.debug("Terminated PoolTask gracefully");
                        actionMonitor.finishedAction(nsr_id, Action.TERMINATED);
                        return;
                    }
                }
            }
        }
        actionMonitor.finishedAction(nsr_id);
    }
}