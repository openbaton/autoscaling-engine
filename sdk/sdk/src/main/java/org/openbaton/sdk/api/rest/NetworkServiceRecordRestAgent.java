package org.openbaton.sdk.api.rest;

import org.openbaton.catalogue.mano.descriptor.VNFComponent;
import org.openbaton.catalogue.mano.descriptor.VNFDependency;
import org.openbaton.catalogue.mano.record.NetworkServiceRecord;
import org.openbaton.catalogue.mano.record.PhysicalNetworkFunctionRecord;
import org.openbaton.catalogue.mano.record.VNFRecordDependency;
import org.openbaton.catalogue.mano.record.VirtualNetworkFunctionRecord;
import org.openbaton.sdk.api.annotations.Help;
import org.openbaton.sdk.api.exception.SDKException;
import org.openbaton.sdk.api.util.AbstractRestAgent;

import java.util.Arrays;
import java.util.List;

/**
 * OpenBaton image-related commands api requester.
 */
public class NetworkServiceRecordRestAgent extends AbstractRestAgent<NetworkServiceRecord> {

    /**
     * Create a NetworkServiceRecord requester with a given url path
     *
     * @param path the url path used for the api requests
     */
    public NetworkServiceRecordRestAgent(String username, String password, String nfvoIp, String nfvoPort, String path, String version) {
        super(username, password, nfvoIp, nfvoPort, path, version, NetworkServiceRecord.class);
    }

    @Help(help = "Create NetworkServiceRecord from NetworkServiceDescriptor id")
    public NetworkServiceRecord create(final String id) throws SDKException {
        String result = this.requestPost("/" + id);
        return this.mapper.fromJson(result, NetworkServiceRecord.class);
    }

    /**
     *
     */
    @Help(help = "Get all the VirtualNetworkFunctionRecords of NetworkServiceRecord with specific id")
    public List<VirtualNetworkFunctionRecord> getVirtualNetworkFunctionRecords(final String id) throws SDKException {
        String url = id + "/vnfrecords";
        return Arrays.asList((VirtualNetworkFunctionRecord[]) requestGetAll(url, VirtualNetworkFunctionRecord.class));
    }

    /**
     *
     */
    @Help(help = "Get the VirtualNetworkFunctionRecord of NetworkServiceRecord with specific id")
    public VirtualNetworkFunctionRecord getVirtualNetworkFunctionRecord(final String id, final String id_vnf) throws SDKException {
        String url = id + "/vnfrecords" + "/" + id_vnf;
        return (VirtualNetworkFunctionRecord) requestGetWithStatusAccepted(url, VirtualNetworkFunctionRecord.class);
    }

    /**
     *
     */
    @Help(help = "Delete the VirtualNetworkFunctionRecord of NetworkServiceRecord with specific id")
    public void deleteVirtualNetworkFunctionRecord(final String id, final String id_vnf) throws SDKException {
        String url = id + "/vnfrecords" + "/" + id_vnf;
        requestDelete(url);
    }

    /**
     * TODO (check the orchestrator)
     */
    @Help(help = "create VirtualNetworkFunctionRecord")
    public VirtualNetworkFunctionRecord createVNFR(final String idNSR, final VirtualNetworkFunctionRecord virtualNetworkFunctionRecord) throws SDKException {
        String url = idNSR + "/vnfrecords";
        return (VirtualNetworkFunctionRecord) requestPost(url, virtualNetworkFunctionRecord);
    }

    @Help(help = "create VNFCInstance. Aka SCALE OUT")
    public void createVNFCInstance(final String idNSR, final String idVNF, final VNFComponent component) throws SDKException {
        String url = idNSR + "/vnfrecords/" + idVNF + "/vdunits/vnfcinstances";
        requestPost(url, component);
    }

    @Help(help = "create VNFCInstance. Aka SCALE OUT")
    public void createVNFCInstance(final String idNSR, final String idVNF, final String idVDU, final VNFComponent component) throws SDKException {
        String url = idNSR + "/vnfrecords/" + idVNF + "/vdunits/" + idVDU + "/vnfcinstances";
        requestPost(url, component);
    }

    @Help(help = "remove VNFCInstance. Aka SCALE IN")
    public void deleteVNFCInstance(final String idNSR, final String idVNF) throws SDKException {
        String url = idNSR + "/vnfrecords/" + idVNF + "/vdunits/vnfcinstances";
        requestDelete(url);
    }

    @Help(help = "remove VNFCInstance. Aka SCALE IN")
    public void deleteVNFCInstance(final String idNSR, final String idVNF, final String idVDU) throws SDKException {
        String url = idNSR + "/vnfrecords/" + idVNF + "/vdunits/" + idVDU + "/vnfcinstances";
        requestDelete(url);
    }

    @Help(help = "remove VNFCInstance. Aka SCALE IN")
    public void deleteVNFCInstance(final String idNSR, final String idVNF, final String idVDU, final String idVNFCInstance) throws SDKException {
        String url = idNSR + "/vnfrecords/" + idVNF + "/vdunits/" + idVDU + "/vnfcinstances/" + idVNFCInstance;
        requestDelete(url);
    }

    /**
     * TODO (check the orchestrator)
     */
    @Help(help = "update VirtualNetworkFunctionRecord")
    public String updateVNFR(final String idNSR, final String id_vnfr, final VirtualNetworkFunctionRecord virtualNetworkFunctionRecord) throws SDKException {
        String url = idNSR + "/vnfrecords" + "/" + id_vnfr;
        return requestPut(url, virtualNetworkFunctionRecord).toString();

    }

    /**
     *
     */
    @Help(help = "Get all the VirtualNetworkFunctionRecord dependencies of NetworkServiceRecord with specific id")
    public List<VNFDependency> getVNFDependencies(final String idNSR) throws SDKException {
        String url = idNSR + "/vnfdependencies";
        return Arrays.asList((VNFDependency[]) requestGetAll(url, VNFDependency.class));

    }

    /**
     *
     */
    @Help(help = "Get the VirtualNetworkFunctionRecord Dependency of a NetworkServiceRecord with specific id")
    public VNFDependency getVNFDependency(final String idNSR, final String id_vnfd) throws SDKException {
        String url = idNSR + "/vnfdependencies" + "/" + id_vnfd;
        return (VNFDependency) requestGetWithStatusAccepted(url, VNFDependency.class);
    }

    /**
     *
     */
    @Help(help = "Delete the VirtualNetworkFunctionRecord Dependency of a NetworkServiceRecord with specific id")
    public void deleteVNFDependency(final String idNSR, final String id_vnfd) throws SDKException {
        String url = idNSR + "/vnfdependencies" + "/" + id_vnfd;
        requestDelete(url);
    }

    /**
     * TODO (check the orchestrator)
     */
    @Help(help = "Create the VirtualNetworkFunctionRecord Dependency of a NetworkServiceRecord with specific id")
    public VNFRecordDependency postVNFDependency(final String idNSR, final VNFRecordDependency vnfDependency) throws SDKException {
        String url = idNSR + "/vnfdependencies" + "/";
        return (VNFRecordDependency) requestPost(url, vnfDependency);

    }

    /**
     *
     */
    @Help(help = "Update the VirtualNetworkFunctionRecord Dependency of a NetworkServiceRecord with specific id")
    public VNFRecordDependency updateVNFDependency(final String idNSR, final String id_vnfd, final VNFRecordDependency vnfDependency) throws SDKException {
        String url = idNSR + "/vnfdependencies" + "/" + id_vnfd;
        return (VNFRecordDependency) requestPut(url, vnfDependency);
    }

    /**
     * Returns the set of PhysicalNetworkFunctionRecord into a NSD with id
     *
     * @param idNSR : The id of NSD
     * @return Set<PhysicalNetworkFunctionRecord>: The Set of
     * PhysicalNetworkFunctionRecord into NSD
     */
    @Help(help = "Get all the PhysicalNetworkFunctionRecords of a specific NetworkServiceRecord with id")
    public List<PhysicalNetworkFunctionRecord> getPhysicalNetworkFunctionRecords(final String idNSR) throws SDKException {
        String url = idNSR + "/pnfrecords";
        return Arrays.asList((PhysicalNetworkFunctionRecord[]) requestGetAll(url, PhysicalNetworkFunctionRecord.class));
    }

    /**
     * Returns the PhysicalNetworkFunctionRecord
     *
     * @param idNSR     : The NSD id
     * @param idPnf The PhysicalNetworkFunctionRecord id
     * @return PhysicalNetworkFunctionRecord: The PhysicalNetworkFunctionRecord selected
     */
    @Help(help = "Get the PhysicalNetworkFunctionRecord of a NetworkServiceRecord with specific id")
    public PhysicalNetworkFunctionRecord getPhysicalNetworkFunctionRecord(final String idNSR, final String idPnf) throws SDKException {
        String url = idNSR + "/pnfrecords" + "/" + idPnf;
        return (PhysicalNetworkFunctionRecord) requestGetWithStatusAccepted(url, PhysicalNetworkFunctionRecord.class);

    }

    /**
     * Deletes the PhysicalNetworkFunctionRecord with the idPnf
     *
     * @param idNSR  The NSD id
     * @param idPnf  The PhysicalNetworkFunctionRecord id
     */
    @Help(help = "Delete the PhysicalNetworkFunctionRecord of a NetworkServiceRecord with specific id")
    public void deletePhysicalNetworkFunctionRecord(final String idNSR, final String idPnf) throws SDKException {
        String url = idNSR + "/pnfrecords" + "/" + idPnf;
        requestDelete(url);
    }

    /**
     * Stores the PhysicalNetworkFunctionRecord
     *
     * @param physicalNetworkFunctionRecord : The PhysicalNetworkFunctionRecord to be stored
     * @param idNSR                            : The NSD id
     * @return PhysicalNetworkFunctionRecord: The PhysicalNetworkFunctionRecord
     * stored
     */
    @Help(help = "Create the PhysicalNetworkFunctionRecord of a NetworkServiceRecord with specific id")
    public PhysicalNetworkFunctionRecord postPhysicalNetworkFunctionRecord(final String idNSR, final PhysicalNetworkFunctionRecord physicalNetworkFunctionRecord) throws SDKException {
        String url = idNSR + "/pnfrecords" + "/";
        return (PhysicalNetworkFunctionRecord) requestPost(url, physicalNetworkFunctionRecord);

    }

    /**
     * TODO (check the orchestrator)
     * <p/>
     * Edits the PhysicalNetworkFunctionRecord
     *
     * @param physicalNetworkFunctionRecord : The PhysicalNetworkFunctionRecord to be edited
     * @param idNSR                            : The NSD id
     * @return PhysicalNetworkFunctionRecord: The PhysicalNetworkFunctionRecord
     * edited
     */
    @Help(help = "Update the PhysicalNetworkFunctionRecord of a NetworkServiceRecord with specific id")
    public PhysicalNetworkFunctionRecord updatePNFD(final String idNSR, final String idPnf, final PhysicalNetworkFunctionRecord physicalNetworkFunctionRecord) throws SDKException {
        String url = idNSR + "/pnfrecords" + "/" + idPnf;
        return (PhysicalNetworkFunctionRecord) requestPut(url, physicalNetworkFunctionRecord);

    }

}
