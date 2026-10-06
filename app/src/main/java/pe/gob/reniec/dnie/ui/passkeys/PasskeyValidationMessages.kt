package pe.gob.reniec.dnie.ui.passkeys

import pe.gob.reniec.dnie.passkeys.android.*

object PasskeyValidationMessages {
    fun describe(failure: Exception): String {
        val code = (failure as? PasskeyValidationFailure)?.code ?: PasskeyValidationCode.UNKNOWN
        val text = when (code) {
            PasskeyValidationCode.REQUEST_MISSING -> "Android no entregó la solicitud de credenciales. Vuelve a abrirla desde el navegador."
            PasskeyValidationCode.CREATE_OPTIONS -> "La solicitud de registro contiene opciones no compatibles o datos inválidos."
            PasskeyValidationCode.GET_OPTIONS, PasskeyValidationCode.DISCOVERY_OPTIONS -> "La solicitud de inicio de sesión contiene opciones no compatibles o datos inválidos."
            PasskeyValidationCode.CALLER_MISSING -> "Android no entregó la identidad del solicitante. No se mostrarán cuentas."
            PasskeyValidationCode.BROWSER_IDENTITY -> "No se pudo verificar el navegador contra la lista de confianza. Prueba Chrome actualizado."
            PasskeyValidationCode.BROWSER_HASH -> "El navegador no entregó un clientDataHash válido."
            PasskeyValidationCode.ORIGIN_INVALID -> "El navegador entregó un origen web inválido."
            PasskeyValidationCode.NATIVE_HASH -> "La aplicación entregó un hash delegado sin un origen de navegador autorizado."
            PasskeyValidationCode.NATIVE_SIGNER -> "No se pudo validar la firma de la aplicación solicitante."
            PasskeyValidationCode.DAL_NETWORK -> "No se pudo consultar la autorización del sitio. Comprueba la conexión a internet."
            PasskeyValidationCode.DAL_DENIED -> "El sitio no delega credenciales a la aplicación solicitante y su firma. Prueba el sitio directamente en Chrome."
            PasskeyValidationCode.SELECTION -> "Android entregó una selección de credenciales ambigua."
            PasskeyValidationCode.UNKNOWN -> "No se completó la validación de la solicitud."
        }
        return "$text [PK_${code.name}]"
    }
}
