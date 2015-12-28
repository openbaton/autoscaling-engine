package org.openbaton.autoscaling.core.management;

import org.openbaton.autoscaling.core.decision.DecisionManagement;
import org.openbaton.autoscaling.core.detection.task.DetectionTask;
import org.openbaton.autoscaling.core.detection.DetectionManagement;
import org.openbaton.autoscaling.core.execution.ExecutionManagement;
import org.openbaton.autoscaling.utils.Utils;
import org.openbaton.catalogue.mano.common.AutoScalePolicy;
import org.openbaton.catalogue.mano.record.NetworkServiceRecord;
import org.openbaton.catalogue.mano.record.VirtualNetworkFunctionRecord;
import org.openbaton.exceptions.NotFoundException;
import org.openbaton.sdk.NFVORequestor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.util.*;
import java.util.concurrent.ScheduledFuture;

/**
 * Created by mpa on 27.10.15.
 */
@Service
@Scope("singleton")
public class ElasticityManagement {

    protected Logger log = LoggerFactory.getLogger(this.getClass());

    @Autowired
    private DetectionManagement detectionManagment;

    @Autowired
    private DecisionManagement decisionManagement;

    @Autowired
    private ExecutionManagement executionManagement;

    @Autowired
    private PoolManagement poolManagement;

    private NFVORequestor nfvoRequestor;

    private Properties properties;

    @PostConstruct
    public void init() {
        properties = Utils.loadProperties();
        log.debug("Properties: " + properties.toString());
        this.nfvoRequestor = new NFVORequestor(properties.getProperty("openbaton-username"), properties.getProperty("openbaton-password"), properties.getProperty("openbaton-url"), properties.getProperty("openbaton-port"), "1");
        detectionManagment.init(properties);
        decisionManagement.init(properties);
        executionManagement.init(properties);
    }

    public void activate(NetworkServiceRecord nsr) throws NotFoundException {
        log.debug("==========ACTIVATE============");
        for (VirtualNetworkFunctionRecord vnfr : nsr.getVnfr()) {
            if (vnfr.getType().equals("media-server")) {
                if (vnfr.getAuto_scale_policy().size() > 0)
                    activate(vnfr);
            }
        }
    }

    public void activate(VirtualNetworkFunctionRecord vnfr) throws NotFoundException {
        log.debug("Activating Elasticity for VNFR " + vnfr.getId());
    }

    public void deactivate(NetworkServiceRecord nsr) {
        log.debug("Deactivating Elasticity for all VNFRs of NSR with id: " + nsr.getId());
        for (VirtualNetworkFunctionRecord vnfr : nsr.getVnfr()) {
                deactivate(vnfr);
        }
        log.debug("Deactivated Elasticity for all VNFRs of NSR with id: " + nsr.getId());
    }

    public void deactivate(VirtualNetworkFunctionRecord vnfr) {
        log.debug("Deactivating Elasticity for VNFR " + vnfr.getId());
    }
}
