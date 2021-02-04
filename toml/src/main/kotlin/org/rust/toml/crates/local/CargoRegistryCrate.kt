/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.toml.crates.local

import com.google.gson.Gson
import com.vdurmont.semver4j.Semver
import com.vdurmont.semver4j.SemverException

data class CargoRegistryCrate(val versions: List<CargoRegistryCrateVersion>) {
    val latestVersion: CargoRegistryCrateVersion?
        // TODO: List<T>.max() is deprecated since Kotlin 1.4
        get() = versions.max()
}

data class CargoRegistryCrateVersion private constructor(
    val version: Semver,
    val isYanked: Boolean,
    val features: List<String>
) : Comparable<CargoRegistryCrateVersion> {
    override fun compareTo(other: CargoRegistryCrateVersion): Int {
        return this.version.compareTo(other.version)
    }

    companion object {
        fun fromJson(json: String): CargoRegistryCrateVersion? {
            data class ParsedVersion(
                val name: String,
                val vers: String,
                val yanked: Boolean,
                val features: HashMap<String, List<String>>
            )

            val parsedVersion = Gson().fromJson(json, ParsedVersion::class.java)

            return create(
                parsedVersion.vers,
                parsedVersion.yanked,
                parsedVersion.features.map { it.key }
            )
        }

        fun create(version: String, isYanked: Boolean, features: List<String>): CargoRegistryCrateVersion? {
            val semverVersion = try {
                Semver(version, Semver.SemverType.NPM)
            } catch (e: SemverException) {
                return null
            }

            return CargoRegistryCrateVersion(semverVersion, isYanked, features)
        }
    }
}
