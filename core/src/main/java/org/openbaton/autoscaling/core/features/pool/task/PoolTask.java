package org.openbaton.autoscaling.core.features.pool.task;

import org.openbaton.autoscaling.core.decision.DecisionManagement;
import org.openbaton.autoscaling.core.detection.DetectionEngine;
import org.openbaton.autoscaling.core.features.pool.PoolEngine;
import org.openbaton.autoscaling.core.features.pool.PoolManagement;
import org.openbaton.catalogue.mano.common.AutoScalePolicy;
import org.openbaton.catalogue.mano.common.ScalingAlarm;
import org.openbaton.catalogue.mano.record.VNFCInstance;
import org.openbaton.catalogue.mano.record.VirtualNetworkFunctionRecord;
import org.openbaton.catalogue.nfvo.Item;
import org.openbaton.exceptions.MonitoringException;
import org.openbaton.exceptions.NotFoundException;
import org.openbaton.exceptions.VimException;
import org.openbaton.sdk.NFVORequestor;
import org.openbaton.sdk.api.exception.SDKException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
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

    private String nsr_id;

    private int pool_size;

    private String name;

    public PoolTask(String nsr_id, int pool_size, PoolEngine poolEngine) throws NotFoundException {
        this.nsr_id = nsr_id;
        this.pool_size = pool_size;
        this.poolEngine = poolEngine;
        this.name = "PoolTask#" + nsr_id;

    }

    @Override
    public void run() {
        log.debug("Checking the pool of reserved VNFCInstances for NSR with id: " + nsr_id);
        Map<String, Map<String, Set<VNFCInstance>>> reservedInstances = poolEngine.getReservedInstances(nsr_id);
        log.debug("Currently reserved VNFCInstances: " + reservedInstances);
        for (String vnfr_id : reservedInstances.keySet()) {
            for (String vdu_id : reservedInstances.get(vnfr_id).keySet()) {
                int currentPoolSize = reservedInstances.get(vnfr_id).get(vdu_id).size();
                log.debug("Current pool size of NSR::VNFR::VDU: " + nsr_id + "::" + vnfr_id + "::" + vdu_id + " -> " + currentPoolSize);
                Set<VNFCInstance> newReservedInstances = new HashSet<>();
                for (int i = currentPoolSize; i <= pool_size ; i++) {
                    log.debug("Allocating new reserved Instance to the pool of NSR::VNFR::VDU: " + nsr_id + "::" + vnfr_id + "::" + vdu_id);
                    try {
                        VNFCInstance newReservedInstance = poolEngine.allocateNewInstance(nsr_id, vnfr_id, vdu_id);
                        newReservedInstances.add(newReservedInstance);
                        log.debug("Allocated new reserved Instance to the pool of NSR::VNFR::VDU: " + nsr_id + "::" + vnfr_id + "::" + vdu_id + " -> " + newReservedInstance);
                    } catch (NotFoundException e) {
                        log.error(e.getMessage(), e);
                    } catch (VimException e) {
                        log.error(e.getMessage(), e);
                    }
                }
                reservedInstances.get(vnfr_id).get(vdu_id).addAll(newReservedInstances);
            }
        }
    }
}