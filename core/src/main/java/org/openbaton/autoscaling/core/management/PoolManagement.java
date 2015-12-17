package org.openbaton.autoscaling.core.management;

import org.openbaton.autoscaling.core.detection.DetectionManagement;
import org.openbaton.autoscaling.core.detection.task.DetectionTask;
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

import java.util.*;
import java.util.concurrent.ScheduledFuture;

/**
 * Created by mpa on 27.10.15.
 */
@Service
@Scope("singleton")
public class PoolManagement {

    protected Logger log = LoggerFactory.getLogger(this.getClass());

    @Autowired
    private DetectionManagement detectionManagment;

    public void activate(VirtualNetworkFunctionRecord vnfr, int pool_size, int pool_check_period) throws NotFoundException {
        log.debug("Activating pool mechanism for VNFR " + vnfr.getId());
    }

    public void deactivate(VirtualNetworkFunctionRecord vnfr) {
        log.debug("Deactivating pool mechanism for VNFR " + vnfr.getId());
    }
}
