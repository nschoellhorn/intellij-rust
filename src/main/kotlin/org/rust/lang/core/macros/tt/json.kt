/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.macros.tt

import com.google.gson.*
import java.lang.reflect.Type

class TokenTreeAdapter : JsonSerializer<TokenTree>, JsonDeserializer<TokenTree> {
    override fun serialize(json: TokenTree, type: Type, context: JsonSerializationContext): JsonElement {
        return context.serialize(json, json.javaClass)
    }

    @Throws(JsonParseException::class)
    override fun deserialize(json: JsonElement, type: Type, context: JsonDeserializationContext): TokenTree? {
        val obj = json.asJsonObject
        val concreteType = when {
            obj.has("Leaf") -> TokenTree.Leaf::class.java
            obj.has("Subtree") -> TokenTree.Subtree::class.java
            else -> return null
        }
        return context.deserialize(json, concreteType)
    }
}

class LeafSAdapter : JsonSerializer<LeafS>, JsonDeserializer<LeafS> {
    override fun serialize(json: LeafS, type: Type, context: JsonSerializationContext): JsonElement {
        return context.serialize(json, json.javaClass)
    }

    @Throws(JsonParseException::class)
    override fun deserialize(json: JsonElement, type: Type, context: JsonDeserializationContext): LeafS? {
        val obj = json.asJsonObject
        val concreteType = when {
            obj.has("Literal") -> LeafS.Literal::class.java
            obj.has("Punct") -> LeafS.Punct::class.java
            obj.has("Ident") -> LeafS.Ident::class.java
            else -> return null
        }
        return context.deserialize(json, concreteType)
    }
}
