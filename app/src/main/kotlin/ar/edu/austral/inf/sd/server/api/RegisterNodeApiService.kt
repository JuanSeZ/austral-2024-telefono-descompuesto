package ar.edu.austral.inf.sd.server.api

import ar.edu.austral.inf.sd.server.model.RegisterResponse

interface RegisterNodeApiService {

    /**
     * POST /register-node
     * Registra un nuevo nodo
     *
     * @param host  (optional)
     * @param port  (optional)
     * @param name  (optional)
     * @return Todo bien (status code 200)
     * @see RegisterNodeApi#registerNode
     */
    fun registerNode(host: kotlin.String?, port: kotlin.Int?, name: kotlin.String?): RegisterResponse
}
