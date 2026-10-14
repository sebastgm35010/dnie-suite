package pe.gob.reniec.dnie.desktop.continuous

import java.nio.file.Path
import pe.gob.reniec.dnie.desktop.cli.DesktopPdfOptions
import pe.gob.reniec.dnie.desktop.remote.NetworkTargets
import pe.gob.reniec.dnie.signing.link.SigningProvision

internal data class ContinuousSuiteOptions(val pcHint: String,val phoneHint: String,val workspace: Path,val identity: Path,val pdf: DesktopPdfOptions) {
    companion object {
        fun parse(args: Array<String>): ContinuousSuiteOptions {
            require(args.size in 5..65 && args[0]=="suite" && args.size%2==1)
            val values=linkedMapOf<String,String>();val pdf=DesktopPdfOptions()
            for(i in 1 until args.size step 2) {
                val key=args[i];val value=args[i+1]
                require(values.put(key,value)==null) {"Duplicate option"}
                if(key !in setOf("--pc","--phone","--workspace","--identity"))pdf.accept(key,value)
            }
            val pc=values["--pc"].orEmpty();val phone=values["--phone"].orEmpty()
            require(pc.isEmpty() || SigningProvision.privateAddress(pc))
            require(phone.isEmpty() || SigningProvision.privateAddress(phone) || NetworkTargets.tailnetHost(phone))
            val workspace=requireNotNull(values["--workspace"]).also {require(it.isNotBlank())}
            val identity=requireNotNull(values["--identity"]).also {require(it.isNotBlank())}
            return ContinuousSuiteOptions(pc,phone,Path.of(workspace).toAbsolutePath(),Path.of(identity).toAbsolutePath(),pdf)
        }
    }
}
