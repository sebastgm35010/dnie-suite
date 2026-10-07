package pe.gob.reniec.dnie.desktop.remote

import pe.gob.reniec.dnie.desktop.cli.CliOptions
import pe.gob.reniec.dnie.desktop.model.DesktopSigningRequest
import pe.gob.reniec.dnie.signing.link.SigningProvision

data class RemoteOptions(val request: DesktopSigningRequest, val pc: String, val phone: String) {
    companion object {
        fun parse(args: Array<String>): RemoteOptions {
            require(args.firstOrNull() == "remote")
            var pc: String? = null; var phone: String? = null
            val documents = mutableListOf("demo") // Reuse document argument validation, not backend execution.
            var index = 1
            while (index < args.size) {
                val flag = args[index++]
                require(index < args.size)
                val value = args[index++]
                when (flag) {
                    "--pc" -> { require(pc == null); pc = value }
                    "--phone" -> { require(phone == null); phone = value }
                    "--input", "--workspace" -> { documents.add(flag); documents.add(value) }
                    else -> error("Unsupported option")
                }
            }
            val pcAddress = requireNotNull(pc); val phoneAddress = requireNotNull(phone)
            require(SigningProvision.privateAddress(pcAddress) && SigningProvision.privateAddress(phoneAddress))
            return RemoteOptions(CliOptions.parse(documents.toTypedArray()), pcAddress, phoneAddress)
        }
    }
}
