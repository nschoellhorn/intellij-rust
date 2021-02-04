/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.toml.crates.local

import com.intellij.openapi.components.service

interface CratesLocalIndexService {
    fun getCrate(crateName: String): CargoRegistryCrate?
    fun getAllCrateNames(): List<String>

    companion object {
        fun getInstance(): CratesLocalIndexService = service()
    }
}
