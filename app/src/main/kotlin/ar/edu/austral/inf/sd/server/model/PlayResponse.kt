package ar.edu.austral.inf.sd.server.model

import java.util.Objects
import ar.edu.austral.inf.sd.server.model.Signatures
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
 * @param contentResult el resultado del contenido
 * @param originalContentType el tipo de contenido que enviaste
 * @param originalLength la longitud original que enviaste
 * @param originalHash el hash del mensaje original que enviaste
 * @param receivedContentType el tipo de contenido que volvió
 * @param receivedLength la longitud que volvió
 * @param receivedHash el hash del mensaje que volvió
 * @param signatures 
 */
data class PlayResponse(

    @get:JsonProperty("contentResult", required = true) val contentResult: kotlin.String,

    @get:JsonProperty("originalContentType", required = true) val originalContentType: kotlin.String,

    @get:JsonProperty("originalLength", required = true) val originalLength: kotlin.Int,

    @get:JsonProperty("originalHash", required = true) val originalHash: kotlin.String,

    @get:JsonProperty("receivedContentType", required = true) val receivedContentType: kotlin.String,

    @get:JsonProperty("receivedLength", required = true) val receivedLength: kotlin.Int,

    @get:JsonProperty("receivedHash", required = true) val receivedHash: kotlin.String,

    @field:Valid
    @get:JsonProperty("signatures", required = true) val signatures: Signatures
    ) {

}

