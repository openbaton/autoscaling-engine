package org.openbaton.autoscaling.core.features.pool;

import org.openbaton.autoscaling.core.detection.DetectionManagement;
import org.openbaton.autoscaling.core.detection.task.DetectionTask;
import org.openbaton.catalogue.mano.common.AutoScalePolicy;
import org.openbaton.catalogue.mano.descriptor.VNFComponent;
import org.openbaton.catalogue.mano.descriptor.VNFDConnectionPoint;
import org.openbaton.catalogue.mano.descriptor.VirtualDeploymentUnit;
import org.openbaton.catalogue.mano.record.NetworkServiceRecord;
import org.openbaton.catalogue.mano.record.VNFCInstance;
import org.openbaton.catalogue.mano.record.VirtualNetworkFunctionRecord;
import org.openbaton.exceptions.NotFoundException;
import org.openbaton.exceptions.VimException;
import org.openbaton.nfvo.vim_interfaces.resource_management.ResourceManagement;
import org.openbaton.sdk.NFVORequestor;
import org.openbaton.sdk.api.exception.SDKException;
import org.openbaton.vim.drivers.exceptions.VimDriverException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Scope;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledFuture;

/**
 * Created by mpa on 27.10.15.
 */
@Service
@Scope("singleton")
public class PoolManagement {

    protected Logger log = LoggerFactory.getLogger(this.getClass());

    @Autowired
    private ConfigurableApplicationContext context;

    private ThreadPoolTaskScheduler taskScheduler;

    private Map<String, Map<String, Map<String, ScheduledFuture>>> tasks;

    @Autowired
    private NFVORequestor nfvoRequestor;

    private ResourceManagement resourceManagement;

    private Map<String, Map<String, Map<String, Set<VNFCInstance>>>> reservedInstances;

    private Properties properties;

    private int pool_size;

    private int pool_check_period;

    //@PostConstruct
    public void init(Properties properties) {
        resourceManagement = (ResourceManagement) context.getBean("openstackVIM", "15672");
        this.properties = properties;
        this.pool_size = Integer.parseInt(properties.getProperty("pool_size"));
        this.pool_check_period = Integer.parseInt(properties.getProperty("pool_check_period"));
        this.nfvoRequestor = new NFVORequestor(properties.getProperty("openbaton-username"), properties.getProperty("openbaton-password"), properties.getProperty("openbaton-url"), properties.getProperty("openbaton-port"), "1");
        this.tasks = new HashMap<>();
        this.taskScheduler = new ThreadPoolTaskScheduler();
        this.taskScheduler.setPoolSize(10);
        this.taskScheduler.setWaitForTasksToCompleteOnShutdown(true);
        this.taskScheduler.initialize();
    }


    public void activate(String nsr_id) throws NotFoundException {
        log.debug("Activating pool mechanism for VNFR " + nsr_id);
        NetworkServiceRecord nsr = null;
        try {
            nsr = nfvoRequestor.getNetworkServiceRecordAgent().findById(nsr_id);
        } catch (SDKException e) {
            log.error(e.getMessage(), e);
        } catch (ClassNotFoundException e) {
            log.error(e.getMessage(), e);
        }
        if (nsr == null) {
            throw new NotFoundException("Not Found NetworkServiceDescriptor with id: " + nsr_id);
        }
        for (VirtualNetworkFunctionRecord vnfr : nsr.getVnfr()) {
            for (VirtualDeploymentUnit vdu : vnfr.getVdu()) {
                allocateNewInstance(nsr, vnfr, vdu);
            }
        }
    }

    public void deactivate(VirtualNetworkFunctionRecord vnfr) {
        log.debug("Deactivating pool mechanism for VNFR " + vnfr.getId());
    }

    public Map<String, Map<String, Set<VNFCInstance>>> getReservedInstances(String nsr_id) {
        if (reservedInstances.containsKey(nsr_id)) {
            if (reservedInstances.get(nsr_id) != null) {
                return reservedInstances.get(nsr_id);
            }
        } else {
            log.warn("Not found any reserved VNFCInstance for NSR with id: " + nsr_id);
        }
        return new HashMap<>();
    }

    public VNFCInstance allocateReservedInstance(String nsr_id, String vnfr_id, String vdu_id) {
        return null;
    }

    public VNFCInstance allocateNewInstance(String nsr_id, String vnfr_id, String vdu_id) {
        return null;
    }

    public VNFCInstance allocateNewInstance(NetworkServiceRecord nsr, VirtualNetworkFunctionRecord vnfr, VirtualDeploymentUnit vdu) {
        if (vdu.getVnfc().iterator().hasNext()) {
            VNFComponent vnfComponentCopy = vdu.getVnfc().iterator().next();
            VNFComponent vnfComponentNew = new VNFComponent();
            vnfComponentNew.setConnection_point(new HashSet<VNFDConnectionPoint>());
            for (VNFDConnectionPoint vnfdConnectionPointCopy : vnfComponentCopy.getConnection_point()) {
                VNFDConnectionPoint vnfdConnectionPointNew = new VNFDConnectionPoint();
                vnfdConnectionPointNew.setFloatingIp(vnfdConnectionPointCopy.getFloatingIp());
                vnfdConnectionPointNew.setVirtual_link_reference(vnfdConnectionPointCopy.getVirtual_link_reference());
                vnfdConnectionPointNew.setType(vnfdConnectionPointCopy.getType());
                vnfComponentNew.getConnection_point().add(vnfdConnectionPointNew);
            }
            Map<String, String> floatgingIps = new HashMap<>();
            for (VNFDConnectionPoint connectionPoint : vnfComponentNew.getConnection_point()){
                if (connectionPoint.getFloatingIp() != null && !connectionPoint.getFloatingIp().equals(""))
                    floatgingIps.put(connectionPoint.getVirtual_link_reference(),connectionPoint.getFloatingIp());
            }
            VNFCInstance vnfcInstance = null;
            try {
                Future<VNFCInstance> vnfcInstanceFuture = resourceManagement.allocate(vdu, vnfr, vnfComponentNew, "", floatgingIps);
                vnfcInstance = vnfcInstanceFuture.get();
            } catch (VimException e) {
                log.error(e.getMessage(), e);
            } catch (VimDriverException e) {
                log.error(e.getMessage(), e);
            } catch (InterruptedException e) {
                log.error(e.getMessage(), e);
            } catch (ExecutionException e) {
                log.error(e.getMessage(), e);
            }
            return vnfcInstance;
        }
        return null;
    }

}
