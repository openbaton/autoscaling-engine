package org.openbaton.sdk.api.rest;

import org.openbaton.catalogue.nfvo.NFVImage;
import org.openbaton.sdk.api.util.AbstractRestAgent;


/**
 * OpenBaton image-related api requester.
 */
public class ImageRestAgent extends AbstractRestAgent<NFVImage> {

    /**
     * Create a image requester with a given url path
     *
     *
     */
    public ImageRestAgent(String username, String password, String nfvoIp, String nfvoPort, String path, String version) {
        super(username, password, nfvoIp, nfvoPort, path, version, NFVImage.class);
    }

}
