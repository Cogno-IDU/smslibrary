package com.eis.communication.header;

import java.io.Serializable;

/**
 * Header is the container of all protocol message's service information
 *
 * @param <T> type of data in which the header information is saved
 *
 * @see Serializable
 *
 * @author Mattia Fanan
 */
public interface Header<T extends Serializable> extends Serializable {
    /**
     * Gets the ID associated with the protocol the message having this header is implementing
     *
     * @return protocol message's ID
     */
    T getID();
}