package org.openbaton.autoscaling.core.execution.task;

import org.openbaton.autoscaling.catalogue.Action;
import org.openbaton.autoscaling.core.execution.ExecutionEngine;
import org.openbaton.autoscaling.core.management.ActionMonitor;
import org.openbaton.catalogue.mano.common.ScalingAction;
import org.openbaton.catalogue.mano.record.Status;
import org.openbaton.catalogue.mano.record.VirtualNetworkFunctionRecord;
import org.openbaton.catalogue.nfvo.messages.Interfaces.NFVMessage;
import org.openbaton.catalogue.nfvo.messages.OrVnfmScalingMessage;
import org.openbaton.catalogue.nfvo.messages.VnfmOrGenericMessage;
import org.openbaton.common.vnfm_sdk.VnfmHelper;
import org.openbaton.common.vnfm_sdk.amqp.VnfmSpringHelperRabbit;
import org.openbaton.exceptions.NotFoundException;
import org.openbaton.exceptions.VimDriverException;
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

    private Properties properties;

    private String nsr_id;

    private String vnfr_id;

    private Set<ScalingAction> actions;

    private String name;

    private ExecutionEngine executionEngine;

    private long cooldown;

    private ActionMonitor actionMonitor;

    public ExecutionTask(String nsr_id, String vnfr_id, Set<ScalingAction> actions, long cooldown, Properties properties, ExecutionEngine executionEngine, ActionMonitor actionMonitor) {
        this.actionMonitor = actionMonitor;
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
        VirtualNetworkFunctionRecord vnfr = null;
        try {
            vnfr = executionEngine.updateVNFRStatus(nsr_id, vnfr_id, Status.SCALING);
        } catch (SDKException e) {
            log.error("Problems with SDK. Cannot update the VNFR. Scaling will not be executed");
            if (log.isDebugEnabled()) {
                log.error(e.getMessage(), e);
            }
            return;
        }
        try {
            for (ScalingAction action : actions) {
                switch (action.getType()) {
                    case SCALE_OUT:
                        executionEngine.scaleOut(vnfr, Integer.parseInt(action.getValue()));
                        break;
                    case SCALE_OUT_TO:
                        executionEngine.scaleOutTo(vnfr, Integer.parseInt(action.getValue()));
                        break;
                    case SCALE_OUT_TO_FLAVOUR:
                        executionEngine.scaleOutToFlavour(vnfr, action.getValue());
                        break;
                    case SCALE_IN:
                        executionEngine.scaleIn(vnfr, Integer.parseInt(action.getValue()));
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
        }catch(SDKException e){
            log.error(e.getMessage(), e);
        }catch(NotFoundException e){
            log.error(e.getMessage(), e);
        }catch(VimException e){
            log.error(e.getMessage(), e);
        } catch (VimDriverException e) {
            e.printStackTrace();
        } finally {
            try {
                executionEngine.updateVNFRStatus(nsr_id, vnfr_id, Status.ACTIVE);
            } catch (SDKException e) {
                log.error("Problems with the SDK. Cannot Update VNFR. VNFR status remains in in SCALE");
                if (log.isDebugEnabled()) {
                    log.error(e.getMessage(), e);
                }
            }
            if (actionMonitor.getAction(vnfr_id) == Action.SCALED) {
                executionEngine.startCooldown(nsr_id, vnfr_id, cooldown);
            } else {
                actionMonitor.finishedAction(vnfr_id);
            }
        }
    }
}
