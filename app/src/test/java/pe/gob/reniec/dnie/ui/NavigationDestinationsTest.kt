package pe.gob.reniec.dnie.ui

import org.junit.Assert.*
import org.junit.Test
import pe.gob.reniec.dnie.ui.navigation.Screen

class NavigationDestinationsTest {
    @Test fun initializingHomeFirstCannotLeaveNullDestinationsInSuperclass() {
        // Accessing a nested singleton first initializes its superclass before INSTANCE.
        assertEquals("home", Screen.Home.route)
        val destinations = Screen.items
        destinations.forEach { assertNotNull(it) }
        assertEquals(listOf("home", "certificates", "signer", "vault", "pc"), destinations.map { it.route })
        assertSame(Screen.Certificates, Screen.primaryFor(Screen.Identity.route))
        assertSame(Screen.Signer, Screen.primaryFor(Screen.BatchSigner.route))
        assertTrue(Screen.destinations.contains(Screen.Identity))
        assertTrue(Screen.destinations.contains(Screen.BatchSigner))
        assertEquals(destinations.size, destinations.map { it.route }.distinct().size)
    }
}
