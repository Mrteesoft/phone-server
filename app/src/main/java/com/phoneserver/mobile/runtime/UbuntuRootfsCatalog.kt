package com.phoneserver.mobile.runtime

object UbuntuRootfsCatalog {

    private const val releaseLabel = "Ubuntu 22.04 LTS"
    private const val codename = "jammy"
    private const val imageVersion = "22.04.5"
    private const val sourcePageUrl = "https://cdimage.ubuntu.com/ubuntu-base/releases/jammy/release/"

    private val supportedSources = mapOf(
            "amd64" to sourceFor(
                    architecture = "amd64",
                    sha256 = "242cd8898b33ea806ef5f13b1076ed7c76f9f989d18384452f7166692438ff1a"
            ),
            "arm64" to sourceFor(
                    architecture = "arm64",
                    sha256 = "075d4abd2817a5023ab0a82f5cb314c5ec0aa64a9c0b40fd3154ca3bfdae979f"
            ),
            "armhf" to sourceFor(
                    architecture = "armhf",
                    sha256 = "fd77cb0659326b75c08ce06b6b8649d2e13ef9a704a8e9212fec32cb97d42add"
            )
    )

    fun resolve(architecture: String): UbuntuRootfsSource? {
        return supportedSources[architecture.lowercase()]
    }

    private fun sourceFor(
            architecture: String,
            sha256: String
    ): UbuntuRootfsSource {
        val fileName = "ubuntu-base-$imageVersion-base-$architecture.tar.gz"
        return UbuntuRootfsSource(
                releaseLabel = releaseLabel,
                codename = codename,
                imageVersion = imageVersion,
                architecture = architecture,
                fileName = fileName,
                downloadUrl = "$sourcePageUrl$fileName",
                sha256 = sha256,
                sourcePageUrl = sourcePageUrl
        )
    }
}
