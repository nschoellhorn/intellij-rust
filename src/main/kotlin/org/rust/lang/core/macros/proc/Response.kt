/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.macros.proc

import com.google.gson.*
import com.google.gson.annotations.SerializedName
import org.rust.lang.core.macros.tt.SubtreeS
import java.lang.reflect.Type

sealed class Response {
    data class Error(@SerializedName("Error") val error: ResponseError) : Response()

    //    data class ListMacro(@SerializedName("ListMacro") val listMacroResult: ListMacrosResult) : Response()
    data class ExpansionMacro(@SerializedName("ExpansionMacro") val expansionResult: ExpansionResult) : Response()
}

data class ExpansionResult(val expansion: SubtreeS)

data class ResponseError(
//    val code: ErrorCode,
    val message: String,
)

//enum class ErrorCode {
//    ServerErrorEnd,
//    ExpansionError,
//}

class ResponseAdapter : JsonSerializer<Response>, JsonDeserializer<Response> {
    override fun serialize(json: Response, type: Type, context: JsonSerializationContext): JsonElement {
        return context.serialize(json, json.javaClass)
    }

    @Throws(JsonParseException::class)
    override fun deserialize(json: JsonElement, type: Type, context: JsonDeserializationContext): Response? {
        val obj = json.asJsonObject
        val concreteType = when {
            obj.has("Error") -> Response.Error::class.java
            obj.has("ExpansionMacro") -> Response.ExpansionMacro::class.java
            else -> return null
        }
        return context.deserialize(json, concreteType)
    }
}
