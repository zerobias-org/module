package com.zerobias.module.hl7.listener;

import ca.uhn.hl7v2.AcknowledgmentCode;
import ca.uhn.hl7v2.HL7Exception;
import ca.uhn.hl7v2.model.AbstractMessage;
import ca.uhn.hl7v2.model.Message;

import java.io.IOException;

/**
 * Builds MLLP acknowledgements (DESIGN §4.2). {@code accept} → {@code MSA|AA}
 * (returned only after the row is durable, so an AA on the wire means the
 * message survives a restart); {@code error} → {@code MSA|AE}, which tells the
 * sending system to retry.
 */
final class AckBuilder {

    private AckBuilder() {
    }

    static Message accept(Message in) throws HL7Exception, IOException {
        return ((AbstractMessage) in).generateACK();
    }

    static Message error(Message in, String reason) throws HL7Exception, IOException {
        return ((AbstractMessage) in).generateACK(AcknowledgmentCode.AE, new HL7Exception(reason));
    }
}
