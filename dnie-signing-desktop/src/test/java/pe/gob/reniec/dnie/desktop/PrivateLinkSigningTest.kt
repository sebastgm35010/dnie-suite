package pe.gob.reniec.dnie.desktop

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.bouncycastle.cms.CMSProcessableByteArray
import org.bouncycastle.cms.CMSSignedData
import org.bouncycastle.cms.jcajce.JcaSimpleSignerInfoVerifierBuilder
import org.junit.Assert.*
import org.junit.Test
import pe.gob.reniec.dnie.desktop.application.DesktopSigningService
import pe.gob.reniec.dnie.desktop.demo.SimulatedSigningCard
import pe.gob.reniec.dnie.desktop.model.DesktopSigningRequest
import pe.gob.reniec.dnie.desktop.model.SigningBackend
import pe.gob.reniec.dnie.signing.link.*

/** Real CMS/batch engine across the wire codec, but simulated card and in-memory transport.
 * Does not establish TLS, expose PC/SC, install a driver or claim physical DNI evidence.
 */
class PrivateLinkSigningTest {
    @Test fun twoCmsFilesUsePrivateCommandsAndPhoneLocalPin() = runBlocking {
        val folder = Files.createTempDirectory("private-link-")
        try {
            val inputs = listOf(folder.resolve("documento ñ.txt"), folder.resolve("binario.dat"))
            inputs.forEachIndexed { index, path -> Files.write(path, byteArrayOf(index.toByte(), 1, 2)) }
            val physical = SimulatedSigningCard()
            val id = UUID.randomUUID()
            val endpoint = AuthorizedSigningEndpoint(id, physical, SimulatedSigningCard.DEMO_PIN.toCharArray(), inputs.size)
            var requests = 0
            fun wire(frame: SigningFrame): SigningFrame {
                val bytes = ByteArrayOutputStream().also { SigningFrames.write(it, frame) }.toByteArray()
                return SigningFrames.read(ByteArrayInputStream(bytes))!!
            }
            val remote = PrivateSigningCard(id, object : SigningExchange {
                override suspend fun exchange(request: SigningFrame): SigningFrame {
                    requests++
                    return wire(endpoint.process(wire(request)))
                }
                override fun close() { endpoint.close() }
            })
            val result = DesktopSigningService().execute(DesktopSigningRequest(inputs, folder.resolve("jobs")), remote, "", SigningBackend.SIMULATED)
            assertTrue(result.complete)
            assertEquals(1, physical.selections); assertEquals(2, physical.verifications)
            assertEquals(2, physical.signatures); assertEquals(1, physical.closes)
            assertEquals(6, requests) // OPEN, AUTHORIZE, CERT, SIGN, AUTHORIZE, SIGN
            result.documents.forEach { doc ->
                val cms = CMSSignedData(CMSProcessableByteArray(Files.readAllBytes(doc.original)), Files.readAllBytes(doc.signature!!))
                assertTrue(cms.signerInfos.signers.single().verify(JcaSimpleSignerInfoVerifierBuilder().build(physical.certificate)))
            }
        } finally {
            Files.walk(folder).use { paths -> paths.sorted(Comparator.reverseOrder()).forEach(Files::delete) }
        }
    }
}
