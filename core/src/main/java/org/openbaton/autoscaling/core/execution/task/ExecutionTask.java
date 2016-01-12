package org.openbaton.autoscaling.core.execution.task;

import org.openbaton.autoscaling.core.execution.ExecutionEngine;
import org.openbaton.catalogue.mano.common.ScalingAction;
import org.openbaton.catalogue.mano.record.Status;
import org.openbaton.exceptions.NotFoundException;
import org.openbaton.exceptions.VimException;
import org.openbaton.sdk.api.exception.SDKException;
import org.openbaton.vim.drivers.exceptions.VimDriverException;
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

    private Properties properties;

    private String nsr_id;

    private String vnfr_id;

    private Set<ScalingAction> actions;

    private String name;

    private ExecutionEngine executionEngine;

    private long cooldown;

    public ExecutionTask(String nsr_id, String vnfr_id, Set<ScalingAction> actions, long cooldown, Properties properties, ExecutionEngine executionEngine) {
        log.debug("Initializing ExecutionTask for VNFR with id: " + vnfr_id + ". Actions: " + actions);
        this.nsr_id = nsr_id;
        this.vnfr_id = vnfr_id;
        this.actions = actions;
        this.cooldown = cooldown;
        this.properties = properties;
        this.executionEngine = executionEngine;
        this.name = "ExecutionTask#" + nsr_id + ":" + vnfr_id;
    }

    @Override
    public void run() {
        if (executionEngine.requestScaling(vnfr_id) == false) {
            log.warn("Scaling request was rejected by the ExecutionEngine. The VNFR is currently either in SCALING or COOLDOWN period. Actions will not be executed for VNFR " + vnfr_id);
        }
        executionEngine.updateVNFRStatus(nsr_id, vnfr_id, Status.SCALING);
        for (ScalingAction action : actions) {
            try {
                switch (action.getType()) {
                    case SCALE_OUT:
                        executionEngine.scaleOut(nsr_id, vnfr_id);
                        break;
                    case SCALE_OUT_TO:
                        executionEngine.scaleOutTo(nsr_id, vnfr_id, Integer.parseInt(action.getValue()));
                        break;
                    case SCALE_OUT_TO_FLAVOUR:
                        executionEngine.scaleOutToFlavour(nsr_id, vnfr_id, action.getValue());
                        break;
                    case SCALE_IN:
                        executionEngine.scaleIn(nsr_id, vnfr_id);
                        break;
                    case SCALE_IN_TO:
                        executionEngine.scaleInTo(nsr_id, vnfr_id, Integer.parseInt(action.getValue()));
                        break;
                    case SCALE_IN_TO_FLAVOUR:
                        executionEngine.scaleInToFlavour(nsr_id, vnfr_id, action.getValue());
                        break;
                    default:
                        break;
                }
            } catch (SDKException e) {
                log.error(e.getMessage(), e);
            } catch (NotFoundException e) {
                log.error(e.getMessage(), e);
            } catch (VimException e) {
                e.printStackTrace();
            } catch (VimDriverException e) {
                e.printStackTrace();
            } finally {
                executionEngine.updateVNFRStatus(nsr_id, vnfr_id, Status.ACTIVE);
                executionEngine.waitForCooldown(vnfr_id, cooldown);
                executionEngine.finishedScaling(vnfr_id);
            }
        }
    }
}
