package ar.edu.austral.inf.sd.server.api

import ar.edu.austral.inf.sd.server.model.PlayResponse

interface PlayApiService {

    /**
     * POST /play
     * Comienza el juego!
     *
     * @param body El mensaje a enviar por la red telefónica (required)
     * @return La red telefónica funcionó bien! (status code 200)
     *         or La red telefónica falló (status code 503)
     * @see PlayApi#sendMessage
     */
    fun sendMessage(body: kotlin.String): PlayResponse
}
