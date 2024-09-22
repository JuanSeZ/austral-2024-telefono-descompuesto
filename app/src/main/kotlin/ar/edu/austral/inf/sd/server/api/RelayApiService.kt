package ar.edu.austral.inf.sd.server.api

import ar.edu.austral.inf.sd.server.model.Signature
import ar.edu.austral.inf.sd.server.model.Signatures

interface RelayApiService {

    /**
     * POST /relay
     * Firma un mensaje y lo manda al siguiente
     *
     * @param message  (required)
     * @param signatures  (required)
     * @return mensaje recibido y reenviado. (status code 202)
     * @see RelayApi#relayMessage
     */
    fun relayMessage(message: kotlin.String, signatures: Signatures): Signature
}
