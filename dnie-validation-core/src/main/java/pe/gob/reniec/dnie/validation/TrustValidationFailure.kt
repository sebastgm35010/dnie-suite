package pe.gob.reniec.dnie.validation

/** Fixed stages only: diagnostics never include URLs, certificate identities or XML contents. */
enum class TrustStage(val description: String) {
    AAC_RESOURCE("No se pudo cargar o comprobar el ancla de INDECOPI"),
    TSL_DOWNLOAD("No se pudo descargar la TSL de INDECOPI"),
    TSL_XML("No se pudo interpretar el formato XML de la TSL"),
    TSL_SIGNER_PATH("No se pudo verificar el certificado del firmante de la TSL"),
    TSL_KEYINFO_DIGEST("No se pudo verificar la referencia al certificado de la TSL"),
    TSL_PROPERTIES_DIGEST("No se pudieron verificar las propiedades firmadas de la TSL"),
    TSL_ROOT_DIGEST("No se pudo verificar la integridad del contenido de la TSL"),
    TSL_SIGNATURE("No se pudo verificar la firma de la TSL"),
    TSL_METADATA("La fecha o versión de la TSL no pudo validarse"),
    TSL_SERVICES("No se pudieron interpretar los servicios de la TSL"),
    TSL_SIGNER_PIN("El firmante de la TSL no coincide con la política autorizada"),
    TSL_ROLLBACK("La TSL retrocede de versión o cambia sin actualizar su secuencia"),
    SIGNER_CRL_DOWNLOAD("No se pudo descargar la CRL del firmante de la TSL"),
    SIGNER_CRL_VALIDATION("No se pudo verificar la revocación del firmante de la TSL"),
    TSL_STATE("No se pudo leer o guardar la versión de TSL aceptada"),
    PKIX_PATH("No se pudo verificar la ruta del certificado hasta una raíz admitida")
}
class TrustValidationFailure(val stage: TrustStage, cause: Throwable) : Exception(stage.name, cause) {
    val safeCode: String get() = "${stage.name}:${cause?.javaClass?.simpleName.orEmpty()}"
}
internal inline fun <T> trustStep(stage: TrustStage, action: () -> T): T = try { action() }
    catch (failure: TrustValidationFailure) { throw failure }
    catch (failure: Exception) { throw TrustValidationFailure(stage, failure) }
    catch (failure: LinkageError) { throw TrustValidationFailure(stage, failure) }
