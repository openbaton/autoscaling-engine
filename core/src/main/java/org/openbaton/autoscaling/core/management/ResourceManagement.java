/*
 *
 *  * Copyright (c) 2015 Technische Universität Berlin
 *  *  Licensed under the Apache License, Version 2.0 (the "License");
 *  *  you may not use this file except in compliance with the License.
 *  *  You may obtain a copy of the License at
 *  *
 *  *         http://www.apache.org/licenses/LICENSE-2.0
 *  *
 *  *  Unless required by applicable law or agreed to in writing, software
 *  *  distributed under the License is distributed on an "AS IS" BASIS,
 *  *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  *  See the License for the specific language governing permissions and
 *  *  limitations under the License.
 *
 */

package org.openbaton.autoscaling.core.management;

import org.openbaton.autoscaling.utils.Utils;
import org.openbaton.catalogue.mano.common.DeploymentFlavour;
import org.openbaton.catalogue.mano.common.Ip;
import org.openbaton.catalogue.mano.descriptor.VNFComponent;
import org.openbaton.catalogue.mano.descriptor.VNFDConnectionPoint;
import org.openbaton.catalogue.mano.descriptor.VirtualDeploymentUnit;
import org.openbaton.catalogue.mano.record.VNFCInstance;
import org.openbaton.catalogue.mano.record.VirtualNetworkFunctionRecord;
import org.openbaton.catalogue.nfvo.*;
import org.openbaton.exceptions.VimDriverException;
import org.openbaton.exceptions.VimException;
import org.openbaton.plugin.utils.RabbitPluginBroker;
import org.openbaton.vim.drivers.VimDriverCaller;
import org.openbaton.vnfm.configuration.AutoScalingProperties;
import org.openbaton.vnfm.configuration.SpringProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Scope;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.AsyncResult;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.rmi.RemoteException;
import java.util.*;
import java.util.concurrent.Future;

/**
 * Created by mpa on 27.01.16.
 */
@Service
@Scope
public class ResourceManagement {

    protected Logger log = LoggerFactory.getLogger(this.getClass());

    @Autowired
    private SpringProperties springProperties;

    @Autowired
    private AutoScalingProperties autoScalingProperties;

    @Autowired
    private ConfigurableApplicationContext context;

    private VimDriverCaller client;

    private String userdataRaw;

    @PostConstruct
    public void init() {
        userdataRaw = Utils.getUserdata();
        client = (VimDriverCaller) ((RabbitPluginBroker) context.getBean("rabbitPluginBroker")).getVimDriverCaller(autoScalingProperties.getRabbitmq().getBrokerIp(), springProperties.getRabbitmq().getUsername(), springProperties.getRabbitmq().getPassword(), springProperties.getRabbitmq().getPort(),"openstack", "openstack", autoScalingProperties.getRabbitmq().getManagement().getPort());
    }

    public void initializeClient() {
        client = (VimDriverCaller) ((RabbitPluginBroker) context.getBean("rabbitPluginBroker")).getVimDriverCaller(autoScalingProperties.getRabbitmq().getBrokerIp(), springProperties.getRabbitmq().getUsername(), springProperties.getRabbitmq().getPassword(), springProperties.getRabbitmq().getPort(),"openstack", "openstack", autoScalingProperties.getRabbitmq().getManagement().getPort());
    }

    @Async
    public Future<VNFCInstance> allocate(VimInstance vimInstance, VirtualDeploymentUnit vdu, VirtualNetworkFunctionRecord vnfr, VNFComponent vnfComponent) throws VimException {
        log.debug("Launching new VM on VimInstance: " + vimInstance.getName());
        log.debug("VDU is : " + vdu.toString());
        log.debug("VNFR is : " + vnfr.toString());
        log.debug("VNFC is : " + vnfComponent.toString());
        /**
         *  *) choose image
         *  *) ...?
         */

        String image = this.chooseImage(vdu.getVm_image(), vimInstance);

        log.debug("Finding Networks...");
        Set<String> networks = new HashSet<String>();
        for (VNFDConnectionPoint vnfdConnectionPoint : vnfComponent.getConnection_point()) {
            for (Network net : vimInstance.getNetworks())
                if (vnfdConnectionPoint.getVirtual_link_reference().equals(net.getName()))
                    networks.add(net.getExtId());
        }
        log.debug("Found Networks with ExtIds: " + networks);

        String flavorExtId = getFlavorExtID(vnfr.getDeployment_flavour_key(), vimInstance);

        log.debug("Generating Hostname...");
        vdu.setHostname(vnfr.getName());
        String hostname = vdu.getHostname() + "-" + ((int) (Math.random() * 1000));
        log.debug("Generated Hostname: " + hostname);

        log.debug("Using SecurityGroups: " + vimInstance.getSecurityGroups());

        Map<String, String> floatingIps = new HashMap<>();
        for (VNFDConnectionPoint connectionPoint : vnfComponent.getConnection_point()) {
            if (connectionPoint.getFloatingIp() != null && !connectionPoint.getFloatingIp().equals("")) {
                floatingIps.put(connectionPoint.getVirtual_link_reference(), connectionPoint.getFloatingIp());
            }
        }

        String userdata = getUserdata(hostname, vnfr);

        log.debug("Launching VM with params: " + hostname + " - " + image + " - " + flavorExtId + " - " + vimInstance.getKeyPair() + " - " + networks + " - " + vimInstance.getSecurityGroups());
        Server server;

        try {
            if (vimInstance == null)
                throw new NullPointerException("VimInstance is null");
            if (hostname == null)
                throw new NullPointerException("hostname is null");
            if (image == null)
                throw new NullPointerException("image is null");
            if (flavorExtId == null)
                throw new NullPointerException("flavorExtId is null");
            if (vimInstance.getKeyPair() == null)
                throw new NullPointerException("vimInstance.getKeyPair() is null");
            if (networks == null)
                throw new NullPointerException("networks is null");
            if (vimInstance.getSecurityGroups() == null)
                throw new NullPointerException("vimInstance.getSecurityGroups() is null");

            server = client.launchInstanceAndWait(vimInstance, hostname, image, flavorExtId, vimInstance.getKeyPair(), networks, vimInstance.getSecurityGroups(), userdata, floatingIps);
            log.debug("Launched VM with hostname " + hostname + " with ExtId " + server.getExtId() + " on VimInstance " + vimInstance.getName());
        } catch (VimDriverException e) {
            if (log.isDebugEnabled()) {
                log.error("Not launched VM with hostname " + hostname + " successfully on VimInstance " + vimInstance.getName() + ". Caused by: " + e.getMessage(), e);
            } else {
                log.error("Not launched VM with hostname " + hostname + " successfully on VimInstance " + vimInstance.getName() + ". Caused by: " + e.getMessage());
            }
            VNFCInstance vnfcInstance = null;
            VimDriverException vimDriverException = (VimDriverException) e.getCause();
            server = vimDriverException.getServer();
            if (server != null) {
                vnfcInstance = getVnfcInstanceFromServer(vimInstance, vnfComponent, hostname, server, vdu, floatingIps, vnfr);
                if (vnfcInstance != null) {
                    try {
                        client.deleteServerByIdAndWait(vimInstance, vnfcInstance.getVc_id());
                    } catch (RemoteException e1) {
                        log.error(e1.getMessage(), e);
                    } catch (VimDriverException e1) {
                        log.error(e1.getMessage(), e);
                    }
                }
            }
            throw new VimException("Not launched VM with hostname " + hostname + " successfully on VimInstance " + vimInstance.getName() + ". Caused by: " + e.getMessage(), e, vnfcInstance);
        } catch (RemoteException e) {
            log.error("Not launched VM with hostname " + hostname + " successfully on VimInstance " + vimInstance.getName() + ". Caused by: " + e.getMessage());
            throw new VimException(e);
        }

        log.debug("Creating VNFCInstance based on the VM launched previously -> VM: " + server);
        VNFCInstance vnfcInstance = getVnfcInstanceFromServer(vimInstance, vnfComponent, hostname, server, vdu, floatingIps, vnfr);

        log.info("Launched VNFCInstance: " + vnfcInstance + " on VimInstance " + vimInstance.getName());
        return new AsyncResult<>(vnfcInstance);
    }

    @Async
    public Future<Boolean> release(VNFCInstance vnfcInstance, VimInstance vimInstance) throws VimException {
        log.debug("Removing VM with ExtId: " + vnfcInstance.getVc_id() + " from VimInstance " + vimInstance.getName());
        try {
            client.deleteServerByIdAndWait(vimInstance, vnfcInstance.getVc_id());
            log.info("Removed VM with ExtId: " + vnfcInstance.getVc_id() + " from VimInstance " + vimInstance.getName());
        } catch (RemoteException e) {
            if (log.isDebugEnabled()) {
                log.error("Not removed VM with ExtId " + vnfcInstance.getVc_id() + " successfully from VimInstance " + vimInstance.getName() + ". Caused by: " + e.getMessage(), e);
            } else {
                log.error("Not removed VM with ExtId " + vnfcInstance.getVc_id() + " successfully from VimInstance " + vimInstance.getName() + ". Caused by: " + e.getMessage());
            }
            throw new VimException("Not removed VM with ExtId " + vnfcInstance.getVc_id() + " successfully from VimInstance " + vimInstance.getName() + ". Caused by: " + e.getMessage(), e);
        } catch (VimDriverException e) {
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e1) {
                log.error(e1.getMessage(), e);
            }
            try {
                client.deleteServerByIdAndWait(vimInstance, vnfcInstance.getVc_id());
            } catch (RemoteException e1) {
                log.error(e1.getMessage(), e);
                throw new VimException("Not removed VM with ExtId " + vnfcInstance.getVc_id() + " successfully from VimInstance " + vimInstance.getName() + ". Caused by: " + e.getMessage(), e);
            } catch (VimDriverException e1) {
                log.error(e1.getMessage(), e);
                throw new VimException("Not removed VM with ExtId " + vnfcInstance.getVc_id() + " successfully from VimInstance " + vimInstance.getName() + ". Caused by: " + e.getMessage(), e);
            }
        }
        return new AsyncResult<Boolean>(true);
    }

    private VNFCInstance getVnfcInstanceFromServer(VimInstance vimInstance, VNFComponent vnfComponent, String hostname, Server server, VirtualDeploymentUnit vdu, Map<String, String> floatingIps, VirtualNetworkFunctionRecord vnfr) {
        VNFCInstance vnfcInstance = new VNFCInstance();
        vnfcInstance.setHostname(hostname);
        vnfcInstance.setVc_id(server.getExtId());
        vnfcInstance.setVim_id(vimInstance.getId());


        if (vnfcInstance.getConnection_point() == null)
            vnfcInstance.setConnection_point(new HashSet<VNFDConnectionPoint>());

        for (VNFDConnectionPoint connectionPoint : vnfComponent.getConnection_point()) {
            VNFDConnectionPoint connectionPoint_vnfci = new VNFDConnectionPoint();
            connectionPoint_vnfci.setVirtual_link_reference(connectionPoint.getVirtual_link_reference());
            connectionPoint_vnfci.setType(connectionPoint.getType());
            for (Map.Entry<String, String> entry : server.getFloatingIps().entrySet())
                if (entry.getKey().equals(connectionPoint.getVirtual_link_reference()))
                    connectionPoint_vnfci.setFloatingIp(entry.getValue());

            vnfcInstance.getConnection_point().add(connectionPoint_vnfci);
        }
        if (vdu.getVnfc_instance() == null) {
            vdu.setVnfc_instance(new HashSet<VNFCInstance>());
        }
        vnfcInstance.setVnfComponent(vnfComponent);

        vnfcInstance.setIps(new HashSet<Ip>());
        vnfcInstance.setFloatingIps(new HashSet<Ip>());

        if (floatingIps.size() != 0) {
            for (Map.Entry<String, String> fip : server.getFloatingIps().entrySet()) {
                Ip ip = new Ip();
                ip.setNetName(fip.getKey());
                ip.setIp(fip.getValue());
                vnfcInstance.getFloatingIps().add(ip);
            }
        }

        if (vdu.getVnfc_instance() == null)
            vdu.setVnfc_instance(new HashSet<VNFCInstance>());

        for (Map.Entry<String, List<String>> network : server.getIps().entrySet()) {
            Ip ip = new Ip();
            ip.setNetName(network.getKey());
            ip.setIp(network.getValue().iterator().next());
            vnfcInstance.getIps().add(ip);
            for (String ip1 : server.getIps().get(network.getKey())) {
                vnfr.getVnf_address().add(ip1);
            }
        }
        return vnfcInstance;
    }

    private String getFlavorExtID(String key, VimInstance vimInstance) throws VimException {
        log.debug("Finding DeploymentFlavor with name: " + key + " on VimInstance " + vimInstance.getName());
        for (DeploymentFlavour deploymentFlavour : vimInstance.getFlavours()) {
            if (deploymentFlavour.getFlavour_key().equals(key) || deploymentFlavour.getExtId().equals(key) || deploymentFlavour.getId().equals(key)) {
                log.info("Found DeploymentFlavour with ExtId: " + deploymentFlavour.getExtId() + " of DeploymentFlavour with name: " + key + " on VimInstance " + vimInstance.getName());
                return deploymentFlavour.getExtId();
            }
        }
        log.error("Not found DeploymentFlavour with name: " + key + " on VimInstance " + vimInstance.getName());


        throw new VimException("Not found DeploymentFlavour with name: " + key + " on VimInstance " + vimInstance.getName());
    }

    private String chooseImage(Collection<String> vm_images, VimInstance vimInstance) throws VimException {
        log.debug("Choosing Image...");
        log.debug("Requested: " + vm_images);
        log.debug("Available: " + vimInstance.getImages());
        if (vm_images != null && vm_images.size() > 0) {
            for (String image : vm_images) {
                for (NFVImage nfvImage : vimInstance.getImages()) {
                    if (image.equals(nfvImage.getName()) || image.equals(nfvImage.getExtId())) {
                        log.info("Image choosed with name: " + nfvImage.getName() + " and ExtId: " + nfvImage.getExtId());
                        return nfvImage.getExtId();
                    }
                }
            }
            throw new VimException("Not found any Image with name: " + vm_images + " on VimInstance " + vimInstance.getName());
        }
        throw new VimException("No Images are available on VimInstnace " + vimInstance.getName());
    }

    private String getUserdata(String hostname, VirtualNetworkFunctionRecord vnfr) {
        log.debug("Preparing userdata");
        Map<String, String> variables = new HashMap<>();
//        variables.put("$HOSTNAME", hostname);
//        variables.put("$MONITORING_URL", mediaServerProperties.getMonitor().getUrl());
//        variables.put("$TURN_SERVER_ACTIVATE", Boolean.toString(mediaServerProperties.getTurnServer().isActivate()));
//        variables.put("$TURN_SERVER_URL", mediaServerProperties.getTurnServer().getUrl());
//        variables.put("$TURN_SERVER_USERNAME", mediaServerProperties.getTurnServer().getUsername());
//        variables.put("$TURN_SERVER_PASSWORD", mediaServerProperties.getTurnServer().getPassword());
//        variables.put("$STUN_SERVER_ACTIVATE", Boolean.toString(mediaServerProperties.getStunServer().isActivate()));
//        variables.put("$STUN_SERVER_ADDRESS", mediaServerProperties.getStunServer().getAddress());
//        variables.put("$STUN_SERVER_PORT", mediaServerProperties.getStunServer().getPort());
        for (ConfigurationParameter configurationParameter : vnfr.getConfigurations().getConfigurationParameters()) {
            log.debug(configurationParameter.toString());
            if (configurationParameter.getConfKey().equals("mediaserver.turn-server.activate")) {
                variables.put("$TURN_SERVER_ACTIVATE", configurationParameter.getValue());
            }
            if (configurationParameter.getConfKey().equals("mediaserver.turn-server.url")) {
                variables.put("$TURN_SERVER_URL", configurationParameter.getValue());
            }
            if (configurationParameter.getConfKey().equals("mediaserver.turn-server.username")) {
                variables.put("$TURN_SERVER_USERNAME", configurationParameter.getValue());
            }
            if (configurationParameter.getConfKey().equals("mediaserver.turn-server.password")) {
                variables.put("$TURN_SERVER_PASSWORD", configurationParameter.getValue());
            }
            if (configurationParameter.getConfKey().equals("mediaserver.stun-server.activate")) {
                variables.put("$STUN_SERVER_ACTIVATE", configurationParameter.getValue());
            }
            if (configurationParameter.getConfKey().equals("mediaserver.stun-server.address")) {
                variables.put("$STUN_SERVER_ADDRESS", configurationParameter.getValue());
            }
            if (configurationParameter.getConfKey().equals("mediaserver.stun-server.port")) {
                variables.put("$STUN_SERVER_PORT", configurationParameter.getValue());
            }
        }
        String userdata = Utils.replaceVariables(userdataRaw, variables);
        log.debug("userdata: " + userdata);
        return userdata;
    }
}
