package pe.gob.reniec.dnie.passkeys

import org.junit.Assert.*
import org.junit.Test

class PasskeyDigitalAssetLinksTest {
    private val fingerprint = List(32) { "AB" }.joinToString(":")
    private fun statement(relation: String = "delegate_permission/common.get_login_creds", signer: String = fingerprint,
        pkg: String = "com.example.app") = """[{"relation":["$relation"],"target":{"namespace":"android_app","package_name":"$pkg","sha256_cert_fingerprints":["$signer"]}}]"""
    @Test fun acceptsExactPackageSignerAndCredentialDelegation() {
        assertTrue(PasskeyDigitalAssetLinks.authorizes(statement(), "com.example.app", fingerprint))
        assertTrue(PasskeyDigitalAssetLinks.authorizes(statement(signer = fingerprint.lowercase()), "com.example.app", fingerprint))
    }
    @Test fun rejectsWrongPackageOldSignerAndNavigationOnlyRelation() {
        assertFalse(PasskeyDigitalAssetLinks.authorizes(statement(pkg = "com.evil.app"), "com.example.app", fingerprint))
        assertFalse(PasskeyDigitalAssetLinks.authorizes(statement(signer = List(32) { "CD" }.joinToString(":")), "com.example.app", fingerprint))
        assertFalse(PasskeyDigitalAssetLinks.authorizes(statement(relation = "delegate_permission/common.handle_all_urls"), "com.example.app", fingerprint))
    }
    @Test fun missingFieldsAndEmptyStatementsDoNotDelegateCredentials() {
        assertFalse(PasskeyDigitalAssetLinks.authorizes("[]", "com.example.app", fingerprint))
        assertFalse(PasskeyDigitalAssetLinks.authorizes("[{}]", "com.example.app", fingerprint))
    }
    @Test fun duplicateFieldsAndExcessiveNestingAreRejected() {
        for (json in listOf("""[{"target":{},"target":{}}]""", "[".repeat(18) + "]".repeat(18))) {
            try { PasskeyDigitalAssetLinks.authorizes(json, "com.example.app", fingerprint); fail("Unexpected success") } catch (_: Exception) {}
        }
    }
}
