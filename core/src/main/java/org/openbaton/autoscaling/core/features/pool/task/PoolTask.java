package org.openbaton.autoscaling.core.features.pool.task;

import org.openbaton.autoscaling.core.decision.DecisionManagement;
import org.openbaton.autoscaling.core.detection.DetectionEngine;
import org.openbaton.autoscaling.core.features.pool.PoolManagement;
import org.openbaton.catalogue.mano.common.AutoScalePolicy;
import org.openbaton.catalogue.mano.common.ScalingAlarm;
import org.openbaton.catalogue.mano.record.VirtualNetworkFunctionRecord;
import org.openbaton.catalogue.nfvo.Item;
import org.openbaton.exceptions.MonitoringException;
import org.openbaton.exceptions.NotFoundException;
import org.openbaton.sdk.NFVORequestor;
import org.openbaton.sdk.api.exception.SDKException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Properties;

/**
 * Created by mpa on 27.10.15.
 */

@Service
@Scope("prototype")
public class PoolTask implements Runnable {

    protected Logger log = LoggerFactory.getLogger(this.getClass());

    @Autowired
    private PoolManagement poolManagement;

    private String nsr_id;

    private String name;

    public PoolTask(String nsr_id) throws NotFoundException {
        this.nsr_id = nsr_id;
        this.name = "PoolTask#" + nsr_id;
    }

    @Override
    public void run() {
        poolManagement.getReservedInstances(nsr_id);
    }
}



