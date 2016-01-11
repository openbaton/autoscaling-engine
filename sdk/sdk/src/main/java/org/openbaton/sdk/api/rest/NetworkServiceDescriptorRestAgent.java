package org.openbaton.sdk.api.rest;

import org.openbaton.catalogue.mano.common.Security;
import org.openbaton.catalogue.mano.descriptor.NetworkServiceDescriptor;
import org.openbaton.catalogue.mano.descriptor.PhysicalNetworkFunctionDescriptor;
import org.openbaton.catalogue.mano.descriptor.VNFDependency;
import org.openbaton.catalogue.mano.descriptor.VirtualNetworkFunctionDescriptor;
import org.openbaton.sdk.api.annotations.Help;
import org.openbaton.sdk.api.exception.SDKException;
import org.openbaton.sdk.api.util.AbstractRestAgent;

import java.util.Arrays;
import java.util.List;

/**
 * OpenBaton network-service-descriptor-related api requester.
 */
public class NetworkServiceDescriptorRestAgent extends AbstractRestAgent<NetworkServiceDescriptor> {

    /**
     * Create a NetworkServiceDescriptor requester with a given url path
     *
     * @param nfvoIp the url path used for the api requests
     */
    public NetworkServiceDescriptorRestAgent(String username, String password, String nfvoIp, String nfvoPort, String path, String version) {
        super(username, password, nfvoIp, nfvoPort, path, version, NetworkServiceDescriptor.class);
    }

    /**
     * Return the list of VirtualNetworkFunctionDescriptor into a NSD with id
     *
     * @param idNSD : The id of NSD
     * @return List<VirtualNetworkFunctionDescriptor>: The List of
     * VirtualNetworkFunctionDescriptor into NSD
     */
    @Help(help = "Get all the VirtualNetworkFunctionDescriptors of a NetworkServiceDescriptor with specific id")
    public List<VirtualNetworkFunctionDescriptor> getVirtualNetworkFunctionDescriptors(final String idNSD) throws SDKException {
        String url = idNSD + "/vnfdescriptors";
        return Arrays.asList((VirtualNetworkFunctionDescriptor[]) requestGetAll(url, VirtualNetworkFunctionDescriptor.class));

    }

    /**
     * Return a VirtualNetworkFunctionDescriptor into a NSD with id
     *
     * @param idNSD     : The id of NSD
     * @param id_vfn : The id of the VNF Descriptor
     * @return List<VirtualNetworkFunctionDescriptor>: The List of
     * VirtualNetworkFunctionDescriptor into NSD
     */
    @Help(help = "Get the VirtualNetworkFunctionDescriptor with specific id of a NetworkServiceDescriptor with specific id")
    public VirtualNetworkFunctionDescriptor getVirtualNetworkFunctionDescriptor(final String idNSD, final String id_vfn) throws SDKException {
        String url = idNSD + "/vnfdescriptors" + "/" + id_vfn;
        return (VirtualNetworkFunctionDescriptor) requestGet(url, VirtualNetworkFunctionDescriptor.class);

    }

    /**
     * Delete the VirtualNetworkFunctionDescriptor
     *
     * @param idNSD     : The id of NSD
     * @param id_vfn : The id of the VNF Descriptor
     */
    @Help(help = "Delete the VirtualNetworkFunctionDescriptor of a NetworkServiceDescriptor with specific id")
    public void deleteVirtualNetworkFunctionDescriptors(final String idNSD, final String id_vfn) throws SDKException {
        String url = idNSD + "/vnfdescriptors" + "/" + id_vfn;
        requestDelete(url);
    }

    /**
     * Create a VirtualNetworkFunctionDescriptor
     *
     * @param virtualNetworkFunctionDescriptor : : the Network Service Descriptor to be updated
     * @param idNSD                               : The id of the networkServiceDescriptor the vnfd shall be created at
     */
    @Help(help = "create the VirtualNetworkFunctionDescriptor of a NetworkServiceDescriptor with specific id")
    public VirtualNetworkFunctionDescriptor createVNFD(final String idNSD, final VirtualNetworkFunctionDescriptor virtualNetworkFunctionDescriptor) throws SDKException {
        String url = idNSD + "/vnfdescriptors" + "/";
        return (VirtualNetworkFunctionDescriptor) requestPost(url, virtualNetworkFunctionDescriptor);
    }

    /**
     * Update the VirtualNetworkFunctionDescriptor
     *
     * @param virtualNetworkFunctionDescriptor : : the Network Service Descriptor to be updated
     * @param idNSD                               : The id of the (old) VNF Descriptor
     * @param id_vfn                           : The id of the VNF Descriptor
     * @return List<VirtualNetworkFunctionDescriptor>: The updated virtualNetworkFunctionDescriptor
     */
    @Help(help = "Update the VirtualNetworkFunctionDescriptor of a NetworkServiceDescriptor with specific id")
    public VirtualNetworkFunctionDescriptor updateVNFD(final String idNSD, final String id_vfn, final VirtualNetworkFunctionDescriptor virtualNetworkFunctionDescriptor) throws SDKException {
        String url = idNSD + "/vnfdescriptors" + "/" + id_vfn;
        return (VirtualNetworkFunctionDescriptor) requestPut(url, virtualNetworkFunctionDescriptor);
    }

    /**
     * Return the list of VNFDependencies into a NSD
     *
     * @param idNSD : The id of the networkServiceDescriptor
     * @return List<VNFDependency>:  The List of VNFDependency into NSD
     */
    @Help(help = "Get all the VirtualNetworkFunctionDescriptor Dependency of a NetworkServiceDescriptor with specific id")
    public List<VNFDependency> getVNFDependencies(final String idNSD) throws SDKException {
        String url = idNSD + "/vnfdependencies";
        return Arrays.asList((VNFDependency[]) requestGetAll(url, VNFDependency.class));

    }

    /**
     * Return a VNFDependency into a NSD
     *
     * @param idNSD      : The id of the VNF Descriptor
     * @param id_vnfd : The VNFDependencies id
     * @return VNFDependency:  The List of VNFDependency into NSD
     */
    @Help(help = "get the VirtualNetworkFunctionDescriptor dependency with specific id of a NetworkServiceDescriptor with specific id")
    public VNFDependency getVNFDependency(final String idNSD, final String id_vnfd) throws SDKException {
        String url = idNSD + "/vnfdependencies" + "/" + id_vnfd;
        return (VNFDependency) requestGet(url, VNFDependency.class);

    }

    /**
     * Delets a VNFDependency
     *
     * @param idNSD      : The id of the networkServiceDescriptor
     * @param id_vnfd : The id of the VNFDependency
     */
    @Help(help = "Delete the VirtualNetworkFunctionDescriptor dependency of a NetworkServiceDescriptor with specific id")
    public void deleteVNFDependency(final String idNSD, final String id_vnfd) throws SDKException {
        String url = idNSD + "/vnfdependencies" + "/" + id_vnfd;
        requestDelete(url);
    }

    /**
     * Create a VNFDependency
     *
     * @param vnfDependency : The VNFDependency to be updated
     * @param idNSD            : The id of the networkServiceDescriptor
     */
    @Help(help = "Create the VirtualNetworkFunctionDescriptor dependency of a NetworkServiceDescriptor with specific id")
    public VNFDependency createVNFDependency(final String idNSD, final VNFDependency vnfDependency) throws SDKException {
        String url = idNSD + "/vnfdependencies" + "/";
        return (VNFDependency) requestPost(url, vnfDependency);

    }

    /**
     * Update the VNFDependency
     *
     * @param vnfDependency : The VNFDependency to be updated
     * @param idNSD            : The id of the networkServiceDescriptor
     * @param id_vnfd       : The id of the VNFDependency
     * @return The updated VNFDependency
     */
    @Help(help = "Update the VirtualNetworkFunctionDescriptor dependency of a NetworkServiceDescriptor with specific id")
    public VNFDependency updateVNFD(final String idNSD, final String id_vnfd, final VNFDependency vnfDependency) throws SDKException {
        String url = idNSD + "/vnfdependencies" + "/" + id_vnfd;
        return (VNFDependency) requestPut(url, vnfDependency);

    }

    /**
     * Return the list of PhysicalNetworkFunctionDescriptor into a NSD with id
     *
     * @param idNSD : The id of NSD
     * @return List<PhysicalNetworkFunctionDescriptor>: The List of
     * PhysicalNetworkFunctionDescriptor into NSD
     */
    @Help(help = "Get all the PhysicalNetworkFunctionDescriptors of a NetworkServiceDescriptor with specific id")
    public List<PhysicalNetworkFunctionDescriptor> getPhysicalNetworkFunctionDescriptors(final String idNSD) throws SDKException {
        String url = idNSD + "/pnfdescriptors";
        return Arrays.asList((PhysicalNetworkFunctionDescriptor[]) requestGetAll(url, PhysicalNetworkFunctionDescriptor.class));

    }

    /**
     * Returns the PhysicalNetworkFunctionDescriptor into a NSD with id
     *
     * @param idNSD     : The NSD id
     * @param idPnf : The PhysicalNetworkFunctionDescriptor id
     * @return PhysicalNetworkFunctionDescriptor: The
     * PhysicalNetworkFunctionDescriptor selected
     */
    @Help(help = "Get the PhysicalNetworkFunctionDescriptor with specific id of a NetworkServiceDescriptor with specific id")
    public PhysicalNetworkFunctionDescriptor getPhysicalNetworkFunctionDescriptor(final String idNSD, final String idPnf) throws SDKException {
        String url = idNSD + "/pnfdescriptors" + "/" + idPnf;
        return (PhysicalNetworkFunctionDescriptor) requestGetWithStatusAccepted(url, PhysicalNetworkFunctionDescriptor.class);
    }

    /**
     * Delete the PhysicalNetworkFunctionDescriptor with the idPnf
     *
     * @param idNSD     : The NSD id
     * @param idPnf : The PhysicalNetworkFunctionDescriptor id
     */
    @Help(help = "Delete the PhysicalNetworkFunctionDescriptor of a NetworkServiceDescriptor with specific id")
    public void deletePhysicalNetworkFunctionDescriptor(final String idNSD, final String idPnf) throws SDKException {
        String url = idNSD + "/pnfdescriptors" + "/" + idPnf;
        requestDelete(url);
    }

    /**
     * Store the PhysicalNetworkFunctionDescriptor
     *
     * @param physicalNetworkFunctionDescriptor    : The PhysicalNetworkFunctionDescriptor to be stored
     * @param idNSD     : The NSD id
     * @return PhysicalNetworkFunctionDescriptor: The PhysicalNetworkFunctionDescriptor stored
     */
    @Help(help = "Create the PhysicalNetworkFunctionDescriptor of a NetworkServiceDescriptor with specific id")
    public PhysicalNetworkFunctionDescriptor createPhysicalNetworkFunctionDescriptor(final String idNSD, final PhysicalNetworkFunctionDescriptor physicalNetworkFunctionDescriptor) throws SDKException {
        String url = idNSD + "/pnfdescriptors";
        return (PhysicalNetworkFunctionDescriptor) requestPost(url, physicalNetworkFunctionDescriptor);

    }

    /**
     * Update the PhysicalNetworkFunctionDescriptor
     *
     * @param physicalNetworkFunctionDescriptor    : The PhysicalNetworkFunctionDescriptor to be edited
     * @param idNSD     : The NSD id
     * @param idPnf : The PhysicalNetworkFunctionDescriptor id
     * @return PhysicalNetworkFunctionDescriptor: The
     * PhysicalNetworkFunctionDescriptor edited
     *
     */
    @Help(help = "Update the PhysicalNetworkFunctionDescriptor of a NetworkServiceDescriptor with specific id")
    public PhysicalNetworkFunctionDescriptor updatePNFD(final String idNSD, final String idPnf, final PhysicalNetworkFunctionDescriptor physicalNetworkFunctionDescriptor) throws SDKException {
        String url = idNSD + "/pnfdescriptors" + "/" + idPnf;
        return (PhysicalNetworkFunctionDescriptor) requestPut(url, physicalNetworkFunctionDescriptor);

    }

    /**
     * Return the Security into a NSD
     *
     * @param idNSD : The id of NSD
     * @return Security: The Security of PhysicalNetworkFunctionDescriptor into
     * NSD
     */
    @Help(help = "Get all the Security of a NetworkServiceDescriptor with specific id")
    public Security getSecurities(final String idNSD) throws SDKException {
        String url = idNSD + "/security";
        return ((Security) requestGet(url, Security.class));
    }


    /**
     * Delete the Security with the id_s
     *
     * @param idNSD   : The NSD id
     * @param id_s : The Security id
     *
     */
    @Help(help = "Delete the Security of a NetworkServiceDescriptor with specific id")
    public void deleteSecurity(final String idNSD, final String id_s) throws SDKException {
        String url = idNSD + "/security" + "/" + id_s;
        requestDelete(url);
    }

    /**
     * Store the Security into NSD
     *
     * @param security : The Security to be stored
     * @param idNSD       : The id of NSD
     * @return Security: The Security stored
     */
    @Help(help = "create the Security of a NetworkServiceDescriptor with specific id")
    public Security createSecurity(final String idNSD, final Security security) throws SDKException {
        String url = idNSD + "/security" + "/";
        return (Security) requestPost(url, security);

    }

    /**
     * Update the Security into NSD
     *
     * @param security : The Security to be stored
     * @param idNSD       : The id of NSD
     * @param id_s     : The security id
     * @return Security: The Security stored
     */
    @Help(help = "Update the Security of a NetworkServiceDescriptor with specific id")
    public Security updateSecurity(final String idNSD, final String id_s, final Security security) throws SDKException {
        String url = idNSD + "/security" + "/" + id_s;
        return (Security) requestPut(url, security);

    }

}
