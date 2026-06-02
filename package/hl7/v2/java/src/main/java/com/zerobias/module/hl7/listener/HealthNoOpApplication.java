package com.zerobias.module.hl7.listener;

import ca.uhn.hl7v2.HL7Exception;
import ca.uhn.hl7v2.model.AbstractMessage;
import ca.uhn.hl7v2.model.Message;
import ca.uhn.hl7v2.protocol.ReceivingApplication;
import ca.uhn.hl7v2.protocol.ReceivingApplicationException;

import java.util.Map;

/**
 * The X01-trigger no-op receiver for the startup MLLP self-test (DESIGN §9),
 * registered for any message type with trigger event {@code X01}: acknowledges
 * with {@code MSA|AA} but <b>never persists</b>, so dialing the listener's own
 * port confirms the receive loop end-to-end without polluting the buffer.
 * {@code X01} is a synthetic trigger reserved for this (no real HL7 feed uses it),
 * so reserving it for health is safe.
 */
final class HealthNoOpApplication implements ReceivingApplication<Message> {

    @Override
    public boolean canProcess(Message message) {
        return true;
    }

    @Override
    public Message processMessage(Message in, Map<String, Object> metadata)
            throws ReceivingApplicationException, HL7Exception {
        try {
            return ((AbstractMessage) in).generateACK();   // AA, no buffering
        } catch (Exception e) {
            throw new ReceivingApplicationException(e);
        }
    }
}
