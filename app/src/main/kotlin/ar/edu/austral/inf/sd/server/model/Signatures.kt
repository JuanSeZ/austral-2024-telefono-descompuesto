package ar.edu.austral.inf.sd.server.model

import java.util.Objects
import ar.edu.austral.inf.sd.server.model.Signature
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
 * @param items 
 */
data class Signatures(

    @field:Valid
    @get:JsonProperty("items", required = true) val items: kotlin.collections.List<Signature>
    ) {

}

