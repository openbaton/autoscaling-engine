package org.openbaton.sdk.api.rest;

import org.openbaton.catalogue.nfvo.EventEndpoint;
import org.openbaton.sdk.api.util.AbstractRestAgent;

public class EventAgent extends AbstractRestAgent<EventEndpoint>{

    public EventAgent(String username, String password, String nfvoIp, String nfvoPort, String path, String version) {
        super(username, password, nfvoIp, nfvoPort, path, version, EventEndpoint.class);
    }
}
