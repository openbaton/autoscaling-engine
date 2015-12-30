package org.openbaton.autoscaling.core.management;

import org.openbaton.autoscaling.core.decision.DecisionManagement;
import org.openbaton.autoscaling.core.detection.DetectionManagement;
import org.openbaton.autoscaling.core.execution.ExecutionManagement;
import org.openbaton.autoscaling.core.features.pool.PoolManagement;
import org.openbaton.autoscaling.utils.Utils;
import org.openbaton.exceptions.NotFoundException;
import org.openbaton.exceptions.VimException;
import org.openbaton.sdk.NFVORequestor;
import org.openbaton.vim.drivers.exceptions.VimDriverException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.util.*;

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
        if (properties.getProperty("pool_activated").equals(true)) {
            poolManagement = new PoolManagement();
            poolManagement.init(properties);
        }
    }

    public void activate(String nsr_id) throws NotFoundException, VimException, VimDriverException {
        log.debug("Activating Elasticity for NSR with id: " + nsr_id);
        detectionManagment.activate(nsr_id);
        if (properties.getProperty("pool_activated").equals(true)) {
            poolManagement.activate(nsr_id);
        }
        log.info("Activated Elasticity for NSR with id: " + nsr_id);
    }

    public void deactivate(String nsr_id) throws NotFoundException {
        log.debug("Deactivating Elasticity for NSR with id: " + nsr_id);
        detectionManagment.deactivate(nsr_id);
        log.info("Deactivated Elasticity for NSR with id: " + nsr_id);
    }
}
