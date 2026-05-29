package com.zerobias.module.hl7.materializer;

import ca.uhn.hl7v2.HL7Exception;
import ca.uhn.hl7v2.model.Message;

/**
 * Turns a parsed HL7 message into the post-normalized typed JSON the
 * DataProducer surface serves (DESIGN §5). The receiver calls this at
 * materialization time and stores the result in {@code mapped_json}.
 *
 * <p>The full, structure-index-driven implementation is Phase 3; {@link
 * EnvelopeMaterializer} is an interim implementation that emits the common
 * envelope/patient fields so the receive→buffer path is exercisable now.
 */
public interface MessageMaterializer {
    String toTypedJson(Message message) throws HL7Exception;
}
