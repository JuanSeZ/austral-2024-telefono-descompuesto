package ar.edu.austral.inf.sd.server.model

import java.util.Objects
import com.fasterxml.jackson.annotation.JsonProperty
import jakarta.validation.constraints.DecimalMax
import jakarta.validation.constraints.DecimalMin
import jakarta.validation.constraints.Email
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size
import jakarta.validation.Valid

/**
 * 
 * @param name El nombre del nodo que firmo
 * @param hash El hash del contenido calculado por el nodo
 * @param contentType El hash de la firma del nodo
 * @param contentLength La longitud del contenido
 */
data class Signature(

    @get:JsonProperty("name", required = true) val name: kotlin.String,

    @get:JsonProperty("hash", required = true) val hash: kotlin.String,

    @get:JsonProperty("contentType") val contentType: kotlin.String? = null,

    @get:JsonProperty("contentLength") val contentLength: kotlin.Int? = null
    ) {

}

