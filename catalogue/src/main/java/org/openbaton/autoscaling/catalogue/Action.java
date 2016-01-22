package org.openbaton.autoscaling.catalogue;

/**
 * Created by mpa on 29.10.15.
 */
public enum Action {
    INACTIVE,
    DETECT,
    DECIDE,
    SCALE,
    COOLDOWN,
    TERMINATING,
    TERMINATED;

}
