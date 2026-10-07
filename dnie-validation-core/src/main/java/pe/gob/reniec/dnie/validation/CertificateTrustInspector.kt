package pe.gob.reniec.dnie.validation

import java.security.cert.*
import java.util.Date

/** Current-time trust only. Historical signature validation requires a separate evidence policy. */
class CertificateTrustInspector(private val lists: TrustedListSource = PeruTrustedListProvider(),
    private val source: PublicCertificateSource = ReniecPublicSource()) {
    fun inspect(der: ByteArray, forDocumentSignature: Boolean = true): CertificateValidationReport {
        val revocation = OnlineRevocationInspector(source).inspect(der, forDocumentSignature)
        if (!revocation.certificate.acceptable) return revocation
        val now = Date()
        val list = try { lists.current(now) } catch (failure: TrustValidationFailure) {
            return revocation.copy(trustDetail = failure.stage.description + "; confianza indeterminada",
                trustFailureCode = failure.safeCode)
        } catch (failure: Exception) {
            return revocation.copy(trustDetail = "TSL no autenticada o no disponible; confianza indeterminada",
                trustFailureCode = "TSL_SOURCE:${failure.javaClass.simpleName}")
        }
        val factory = CertificateFactory.getInstance("X.509")
        val leaf = factory.generateCertificate(der.inputStream()) as X509Certificate
        val identities = list.currentCertificateAuthorities(now)
        val anchors = identities.filter { it.subjectX500Principal == it.issuerX500Principal &&
            runCatching { it.verify(it.publicKey); it.checkValidity(now) }.isSuccess }.map { TrustAnchor(it, null) }.toSet()
        if (anchors.isEmpty()) return revocation.copy(trustDetail = "TSL autenticada sin raíz admisible para esta operación")
        val candidates = identities.toMutableList()
        var current = leaf
        val deadline = System.nanoTime() + 30_000_000_000L
        repeat(5) {
            if (Thread.currentThread().isInterrupted || System.nanoTime() > deadline) return@repeat
            val issuer = candidates.firstOrNull { cert -> cert.subjectX500Principal == current.issuerX500Principal &&
                runCatching { current.verify(cert.publicKey) }.isSuccess } ?: CertificateLocations.issuerUrls(current).firstNotNullOfOrNull { url ->
                try {
                    (factory.generateCertificate(source.download(url).inputStream()) as X509Certificate).takeIf {
                        current.issuerX500Principal == it.subjectX500Principal && runCatching { current.verify(it.publicKey) }.isSuccess
                    }
                } catch (_: Exception) { null }
            }
            if (issuer != null && candidates.none { it.encoded.contentEquals(issuer.encoded) }) candidates += issuer
            if (issuer != null) current = issuer
        }
        val path = try {
            val parameters = PKIXBuilderParameters(anchors, X509CertSelector().apply { certificate = leaf }).apply {
                date = now; isRevocationEnabled = false
                addCertStore(CertStore.getInstance("Collection", CollectionCertStoreParameters(candidates + leaf)))
            }
            CertPathBuilder.getInstance("PKIX").build(parameters) as PKIXCertPathBuilderResult
        } catch (failure: Exception) {
            return revocation.copy(trustDetail = "No se pudo verificar una ruta hasta una raíz admitida por la TSL",
                trustFailureCode = "PKIX_PATH:${failure.javaClass.simpleName}")
        }
        val chain = path.certPath.certificates.map { it as X509Certificate } + requireNotNull(path.trustAnchor.trustedCert)
        val admitted = list.currentCertificateAuthorities(now)
        for (cert in chain.drop(1)) {
            val listed = list.services.any { service -> service.identities.any { it.encoded.contentEquals(cert.encoded) } }
            if (listed && admitted.none { it.encoded.contentEquals(cert.encoded) })
                return revocation.copy(trust = TrustStatus.UNTRUSTED, trustDetail = "Una CA de la ruta no tiene servicio actual admisible en la TSL")
        }
        val allowedSignatures = setOf("1.2.840.113549.1.1.11", "1.2.840.113549.1.1.12", "1.2.840.113549.1.1.13",
            "1.2.840.10045.4.3.2", "1.2.840.10045.4.3.3", "1.2.840.10045.4.3.4")
        if (chain.any { !CertificateChecks.inspect(it, now, false).acceptable } ||
            chain.dropLast(1).any { it.sigAlgOID !in allowedSignatures })
            return revocation.copy(trust = TrustStatus.UNTRUSTED, trustDetail = "Cadena fuera de la política de vigencia o algoritmos")
        var aggregate = revocation.revocation.status
        var expires = minOf(list.expires.time, list.authorityEvidenceUntil?.time ?: list.expires.time,
            chain.minOf { it.notAfter.time }, revocation.revocation.nextUpdate?.time ?: now.time)
        for (index in 1 until chain.lastIndex) {
            val cert = chain[index]; val issuer = chain[index + 1]
            var evidence = RevocationAssessment(RevocationStatus.UNKNOWN, "Sin evidencia de CA")
            if (source is OcspResponseSource) for (url in CertificateLocations.ocspUrls(cert)) {
                if (Thread.currentThread().isInterrupted || System.nanoTime() > deadline) break
                evidence = runCatching { OcspValidator.evaluate(cert, issuer, (source as OcspResponseSource).query(url, OcspValidator.request(cert, issuer)), Date()) }
                    .getOrDefault(evidence)
                if (evidence.status != RevocationStatus.UNKNOWN) break
            }
            if (evidence.status == RevocationStatus.UNKNOWN) for (url in CertificateLocations.completeCrlUrls(cert)) {
                if (Thread.currentThread().isInterrupted || System.nanoTime() > deadline) break
                evidence = runCatching { DirectCrlValidator.evaluate(cert, issuer,
                    factory.generateCRL(source.download(url).inputStream()) as X509CRL, Date()) }.getOrDefault(evidence)
                if (evidence.status != RevocationStatus.UNKNOWN) break
            }
            if (evidence.status == RevocationStatus.REVOKED) aggregate = RevocationStatus.REVOKED
            else if (evidence.status == RevocationStatus.UNKNOWN && aggregate != RevocationStatus.REVOKED) aggregate = RevocationStatus.UNKNOWN
            evidence.nextUpdate?.let { expires = minOf(expires, it.time) }
        }
        if (aggregate == RevocationStatus.GOOD && System.currentTimeMillis() >= expires) aggregate = RevocationStatus.UNKNOWN
        return revocation.copy(trust = TrustStatus.TRUSTED, chainRevocation = aggregate,
            chainValidUntil = if (aggregate == RevocationStatus.GOOD) Date(expires) else null,
            trustDetail = "Cadena PKIX a raíz admitida por TSL Perú autenticada, secuencia ${list.sequence}. Estado actual; no clasificación eIDAS.")
    }
}
