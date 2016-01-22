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
import org.openbaton.vim.drivers.exceptions.VimDriverException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;

import java.util.Properties;
import java.util.Set;

/**
 * Created by mpa on 27.10.15.
 */

@Service
@Scope("prototype")
public class CooldownTask implements Runnable {

    protected Logger log = LoggerFactory.getLogger(this.getClass());

    private Properties properties;

    private String nsr_id;

    private String vnfr_id;

    private String name;

    private ExecutionEngine executionEngine;

    private long cooldown;

    private ActionMonitor actionMonitor;

    public CooldownTask(String nsr_id, String vnfr_id, long cooldown, Properties properties, ExecutionEngine executionEngine, ActionMonitor actionMonitor) {
        this.actionMonitor = actionMonitor;
        log.debug("Initializing CooldownTask for VNFR with id: " + vnfr_id);
        this.nsr_id = nsr_id;
        this.vnfr_id = vnfr_id;
        this.cooldown = cooldown;
        this.properties = properties;
        this.executionEngine = executionEngine;
        this.name = "ExecutionTask#" + nsr_id + ":" + vnfr_id;
    }

    @Override
    public void run() {
        try {
            int i = 0;
            while ( i < cooldown) {
                Thread.sleep(1000);
                log.debug("Waiting for Cooldown ... " + i + "w");
                i++;
                //terminate gracefully at this point in time if suggested from the outside
                if (actionMonitor.isTerminating(vnfr_id)) {
                    actionMonitor.finishedAction(vnfr_id, Action.TERMINATED);
                    return;
                }
            }
        } catch (InterruptedException e) {
            log.warn("Cooldown for VNFR with id: " + vnfr_id + "was interrupted");
        }
        actionMonitor.removeId(vnfr_id);
    }
}
