package org.openbaton.autoscaling.core;

import org.openbaton.autoscaling.catalogue.VnfrMonitor;
import org.openbaton.catalogue.mano.common.AutoScalePolicy;
import org.openbaton.catalogue.mano.descriptor.VNFComponent;
import org.openbaton.catalogue.mano.descriptor.VNFDConnectionPoint;
import org.openbaton.catalogue.mano.descriptor.VirtualDeploymentUnit;
import org.openbaton.catalogue.mano.record.VNFCInstance;
import org.openbaton.catalogue.mano.record.VirtualNetworkFunctionRecord;
import org.openbaton.catalogue.nfvo.Item;
import org.openbaton.exceptions.VimException;
import org.openbaton.monitoring.interfaces.ResourcePerformanceManagement;
import org.openbaton.nfvo.vim_interfaces.resource_management.ResourceManagement;
import org.openbaton.plugin.utils.PluginBroker;
import org.openbaton.vim.drivers.exceptions.VimDriverException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.AutowireCapableBeanFactory;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Scope;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledFuture;

/**
 * Created by mpa on 27.10.15.
 */
@Service
@Scope
public class ElasticityManagement {

    protected Logger log = LoggerFactory.getLogger(this.getClass());

    protected ResourceManagement resourceManagement;

    protected ResourcePerformanceManagement monitor;

    private ThreadPoolTaskScheduler taskScheduler;

    @Autowired
    private AutowireCapableBeanFactory beanFactory;

    private Map<String, Set<ScheduledFuture>> tasks;

    @Autowired
    private ConfigurableApplicationContext context;

    @Autowired
    private VnfrMonitor vnfrMonitor;

    /**
     * Vim must be initialized only after the registry is up and plugin registered
     */
    public void initilizeVim() {
        PluginBroker<ResourcePerformanceManagement> pluginBroker = new PluginBroker<>();
        try {
            this.monitor = pluginBroker.getPlugin("localhost", "monitor", "smart-dummy", "smart", 19345);
        } catch (RemoteException e) {
            log.error(e.getLocalizedMessage(), e);
        } catch (NotBoundException e) {
            log.warn("Monitoring " + e.getLocalizedMessage() + ". ElasticityManagement will not start.", e);
        }
        resourceManagement = (ResourceManagement) context.getBean("openstackVIM", "openstack", 19345);
    }

    @PostConstruct
    private void init() {
        tasks = new HashMap<>();
        this.taskScheduler = new ThreadPoolTaskScheduler();
        this.taskScheduler.setPoolSize(10);
        this.taskScheduler.setWaitForTasksToCompleteOnShutdown(true);
        this.taskScheduler.initialize();
    }

    public void activate(VirtualNetworkFunctionRecord vnfr) {
        log.debug("Activating Elasticity for vnfr " + vnfr.getId());
        if (monitor != null) {
            tasks.put(vnfr.getId(), new HashSet<ScheduledFuture>());
            for (AutoScalePolicy policy : vnfr.getAuto_scale_policy()) {
                ElasticityTask elasticityTask = (ElasticityTask) context.getBean("elasticityTask");
                elasticityTask.init(vnfr, policy);
                //taskExecutor.execute(elasticityTask);
                ScheduledFuture scheduledFuture = taskScheduler.scheduleAtFixedRate(elasticityTask, policy.getPeriod() * 1000);
                tasks.get(vnfr.getId()).add(scheduledFuture);
            }
            log.debug("Activated Elasticity for vnfr " + vnfr.getId());
        } else {
            log.warn("Cannot activate ElasticityManagement because the MonitoringAgent is not available");
        }
    }

    public void deactivate(VirtualNetworkFunctionRecord vnfr) {
        log.debug("Deactivating Elasticity for vnfr " + vnfr.getId());
        if (tasks.containsKey(vnfr.getId())) {
            Set<ScheduledFuture> vnfrTasks = tasks.get(vnfr.getId());
            for (ScheduledFuture scheduledFuture : vnfrTasks) {
                scheduledFuture.cancel(false);
            }
            vnfrMonitor.removeVNFR(vnfr.getId());
            log.debug("Deactivated Elasticity for vnfr " + vnfr.getId());
        } else {
            log.debug("Not Found any ElasticityTasks for VNFR with id: " + vnfr.getId());
        }

    }

    public void scaleVNFComponents(VirtualNetworkFunctionRecord vnfr, AutoScalePolicy autoScalePolicy) {
        if (autoScalePolicy.getAction().equals("scaleup")) {
            for (VirtualDeploymentUnit vdu : vnfr.getVdu()) {
                if (vdu.getVnfc().size() < vdu.getScale_in_out() && (vdu.getVnfc().iterator().hasNext())) {
                    VNFComponent vnfComponent_copy = vdu.getVnfc().iterator().next();
                    VNFComponent vnfComponent_new = new VNFComponent();
                    vnfComponent_new.setConnection_point(new HashSet<VNFDConnectionPoint>());
                    for (VNFDConnectionPoint vnfdConnectionPoint_copy : vnfComponent_copy.getConnection_point()) {
                        VNFDConnectionPoint vnfdConnectionPoint_new = new VNFDConnectionPoint();
                        vnfdConnectionPoint_new.setVirtual_link_reference(vnfdConnectionPoint_copy.getVirtual_link_reference());
                        vnfdConnectionPoint_new.setType(vnfdConnectionPoint_copy.getType());
                        vnfComponent_new.getConnection_point().add(vnfdConnectionPoint_new);
                    }
                    vdu.getVnfc().add(vnfComponent_new);
                    log.debug("SCALING: Added new Component to VDU " + vdu.getId());
                    return;
                } else {
                    continue;
                }
            }
            log.debug("Not found any VDU to scale out a VNFComponent. Limits are reached.");
        } else if (autoScalePolicy.getAction().equals("scaledown")) {
            for (VirtualDeploymentUnit vdu : vnfr.getVdu()) {
                if (vdu.getVnfc().size() > 1 && vdu.getVnfc().iterator().hasNext()) {
                    VNFComponent vnfComponent_remove = vdu.getVnfc().iterator().next();
                    vdu.getVnfc().remove(vnfComponent_remove);
                    log.debug("SCALING: Removed Component " + vnfComponent_remove.getId() + " from VDU " + vdu.getId());
                    return;
                } else {
                    continue;
                }
            }
            log.debug("Not found any VDU to scale in a VNFComponent. Limits are reached.");
        }
    }

    public void scaleVNFCInstances(VirtualNetworkFunctionRecord vnfr) {
        List<Future<VNFCInstance>> vnfcInstances = new ArrayList<>();
        for (VirtualDeploymentUnit vdu : vnfr.getVdu()) {
            //Check for additional components for scaling out
            for (VNFComponent vnfComponent : vdu.getVnfc()) {
                //VNFComponent ID is null -> NEW
                boolean found = false;
                //Check if VNFCInstance for VNFComponent already exists
                for (VNFCInstance vnfcInstance : vdu.getVnfc_instance()) {
                    if (vnfComponent.getId().equals(vnfcInstance.getVnfComponent().getId())) {
                        found = true;
                        break;
                    }
                }
                //If the Instance doesn't exists, allocate a new one
                if (!found) {
                    try {
                        Map<String, String> floatgingIps = new HashMap<>();
                        for (VNFDConnectionPoint connectionPoint : vnfComponent.getConnection_point()){
                            if (connectionPoint.getFloatingIp() != null && !connectionPoint.getFloatingIp().equals(""))
                                floatgingIps.put(connectionPoint.getVirtual_link_reference(),connectionPoint.getFloatingIp());
                        }
                        String userdata = "#userdata";
                        Future<VNFCInstance> allocate = resourceManagement.allocate(vdu, vnfr, vnfComponent, userdata, floatgingIps);
                        vnfcInstances.add(allocate);
                        continue;
                    } catch (VimException e) {
                        log.error(e.getMessage(), e);
                        throw new RuntimeException();
                    } catch (VimDriverException e) {
                        log.error(e.getMessage(), e);
                        throw new RuntimeException();
                    }
                }
            }
            //Check for removed Components to scale in
            Set<VNFCInstance> removed_instances = new HashSet<>();
            for (VNFCInstance vnfcInstance : vdu.getVnfc_instance()) {
                boolean found = false;
                for (VNFComponent vnfComponent : vdu.getVnfc()) {
                    if (vnfcInstance.getVnfComponent().getId().equals(vnfComponent.getId())) {
                        log.debug("VNCInstance: " + vnfcInstance.toString() + " stays");
                        found = true;
                        //VNFComponent is still existing
                        break;
                    }
                }
                //VNFComponent is not exsting anymore -> Remove VNFCInstance
                if (!found) {
                    try {
                        log.debug("VNCInstance: " + vnfcInstance.toString() + " removing");
                        resourceManagement.release(vnfcInstance, vdu.getVimInstance());
                        removed_instances.add(vnfcInstance);
                    } catch (VimException e) {
                        log.error(e.getMessage(), e);
                        throw new RuntimeException();
                    }
                }
            }
            //Remove terminated VNFCInstances
            vdu.getVnfc_instance().removeAll(removed_instances);
        }
        //Print ids of deployed VDUs
        for (Future<VNFCInstance> vnfcInstance : vnfcInstances) {
            try {
                log.debug("Created VNFCInstance with id: " + vnfcInstance.get());
            } catch (InterruptedException e) {
                log.error(e.getMessage(), e);
                throw new RuntimeException(e.getMessage(), e);
            } catch (ExecutionException e) {
                log.error(e.getMessage(), e);
                throw new RuntimeException(e.getMessage(), e);
            }
        }
    }

    public synchronized List<Item> getRawMeasurementResults(VirtualNetworkFunctionRecord vnfr, final String metric, String period) throws RemoteException {
        List<Item> measurementResults = new ArrayList<Item>();
        List<String> hostnames = new ArrayList<String>();
        List<String> metrics = new ArrayList<String>();
        metrics.add(metric);
        log.debug("Getting all measurement results for vnfr " + vnfr.getId() + " on metric " + metric + ".");
        for (VirtualDeploymentUnit vdu : vnfr.getVdu()) {
            for (final VNFCInstance vnfcInstance : vdu.getVnfc_instance()) {
                hostnames.add(vnfcInstance.getHostname());
            }
        }
        measurementResults.addAll(monitor.getMeasurementResults(hostnames, metrics, period));
        log.debug("Got all measurement results for vnfr " + vnfr.getId() + " on metric " + metric + " -> " + measurementResults + ".");
        return measurementResults;
    }

    public double calculateMeasurementResult(AutoScalePolicy autoScalePolicy, List<Item> measurementResults) {
        double result;
        List<Double> consideredResults = new ArrayList<>();
        for (Item measurementResult : measurementResults) {
            consideredResults.add(Double.parseDouble(measurementResult.getValue()));
        }
        switch (autoScalePolicy.getStatistic()) {
            case "avg":
                double sum = 0;
                for (Double consideredResult : consideredResults) {
                    sum += consideredResult;
                }
                result = sum / measurementResults.size();
                break;
            case "min":
                result = Collections.min(consideredResults);
                break;
            case "max":
                result = Collections.max(consideredResults);
                break;
            default:
                result = -1;
                break;
        }
        return result;
    }

    public boolean triggerAction(AutoScalePolicy autoScalePolicy, double result) {
        switch (autoScalePolicy.getComparisonOperator()) {
            case ">":
                if (result > autoScalePolicy.getThreshold()) {
                    return true;
                }
                break;
            case "<":
                if (result < autoScalePolicy.getThreshold()) {
                    return true;
                }
                break;
            case "=":
                if (result == autoScalePolicy.getThreshold()) {
                    return true;
                }
                break;
            default:
                return false;
        }
        return false;
    }

    public boolean checkFeasibility(VirtualNetworkFunctionRecord vnfr, AutoScalePolicy autoScalePolicy) {
        if (autoScalePolicy.getAction().equals("scaleup")) {
            for (VirtualDeploymentUnit vdu : vnfr.getVdu()) {
                if (vdu.getVnfc().size() < vdu.getScale_in_out()) {
                    return true;
                }
            }
            log.debug("Maximum number of instances are reached on all VimInstances");
            return false;
        } else if (autoScalePolicy.getAction().equals("scaledown")) {
            for (VirtualDeploymentUnit vdu : vnfr.getVdu()) {
                if (vdu.getVnfc().size() > 1) {
                    return true;
                }
            }
            log.warn("Cannot terminate the last VDU.");
            return false;
        }
        return true;
    }
}
