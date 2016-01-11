package org.openbaton.sdk.api.rest;

import org.openbaton.catalogue.mano.descriptor.VNFForwardingGraphDescriptor;
import org.openbaton.sdk.api.util.AbstractRestAgent;

/**
 * OpenBaton VNFFG-related api requester.
 */
public class VNFFGRestAgent extends AbstractRestAgent<VNFForwardingGraphDescriptor> {

	/**
	 * Create a VNFFG requester with a given url path
	 *
	 */
	public VNFFGRestAgent(String username, String password, String nfvoIp, String nfvoPort, String path, String version) {
		super(username, password, nfvoIp, nfvoPort, path, version, VNFForwardingGraphDescriptor.class);
	}


}
