package pe.gob.reniec.dnie.ui

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import pe.gob.reniec.dnie.core.apdu.CommandApdu
import pe.gob.reniec.dnie.core.apdu.ResponseApdu
import pe.gob.reniec.dnie.core.logging.ApduDirection
import pe.gob.reniec.dnie.core.logging.ApduLogEntry
import pe.gob.reniec.dnie.core.logging.ApduLogger
import pe.gob.reniec.dnie.ui.console.ConsoleFilter
import pe.gob.reniec.dnie.ui.console.ConsoleViewModel
import pe.gob.reniec.dnie.ui.console.FeedbackDispatcher

@OptIn(ExperimentalCoroutinesApi::class)
class ConsoleViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var apduLogger: ApduLogger
    private lateinit var viewModel: ConsoleViewModel

    private var successFeedbackCount = 0
    private var errorFeedbackCount = 0

    private val mockFeedback = object : FeedbackDispatcher {
        override fun playSuccess() {
            successFeedbackCount++
        }

        override fun playError() {
            errorFeedbackCount++
        }
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        apduLogger = ApduLogger(maxHistorySize = 100)
        successFeedbackCount = 0
        errorFeedbackCount = 0
        viewModel = ConsoleViewModel(apduLogger = apduLogger, feedbackDispatcher = mockFeedback, logDispatcher = testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun testInitialStateIsEmpty() {
        val state = viewModel.uiState.value
        assertTrue(state.entries.isEmpty())
        assertEquals(ConsoleFilter.ALL, state.filter)
        assertTrue(state.autoScroll)
    }

    @Test fun historyRemainsBoundedDuringLongSessions() = runTest(testDispatcher) {
        repeat(25) { batch ->
            repeat(50) { index -> apduLogger.logInfo("Synthetic ${batch * 50 + index}") }
            testDispatcher.scheduler.runCurrent()
        }
        assertEquals(ApduLogger.DEFAULT_MAX_HISTORY, viewModel.uiState.value.entries.size)
        assertEquals("Synthetic 1249", viewModel.uiState.value.entries.last().description)
        assertEquals(0, successFeedbackCount)
        assertEquals(0, errorFeedbackCount)
    }

    @Test
    fun testReactiveLogCollectionAndFiltering() = runTest(testDispatcher) {
        val cmd = CommandApdu(cla = 0x00, ins = 0xA4.toByte(), p1 = 0x04, p2 = 0x00)
        val respOk = ResponseApdu(data = byteArrayOf(), sw1 = 0x90, sw2 = 0x00)
        val respFail = ResponseApdu(data = byteArrayOf(), sw1 = 0x69, sw2 = 0x83)

        apduLogger.logTx(cmd, "SELECT FILE")
        apduLogger.logRx(respOk, 12L, "OK")
        apduLogger.logRx(respFail, 8L, "BLOCKED")

        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(3, state.entries.size)
        assertEquals(3, state.filteredEntries.size)

        // A background console must not vibrate/beep for another feature's APDUs.
        assertEquals(0, successFeedbackCount)
        assertEquals(0, errorFeedbackCount)

        // Filter TX
        viewModel.setFilter(ConsoleFilter.TX)
        val txFiltered = viewModel.uiState.value.filteredEntries
        assertEquals(1, txFiltered.size)
        assertEquals(ApduDirection.TX, txFiltered[0].direction)

        // Filter RX
        viewModel.setFilter(ConsoleFilter.RX)
        val rxFiltered = viewModel.uiState.value.filteredEntries
        assertEquals(2, rxFiltered.size)
        assertTrue(rxFiltered.all { it.direction == ApduDirection.RX })

        // Filter ALL
        viewModel.setFilter(ConsoleFilter.ALL)
        assertEquals(3, viewModel.uiState.value.filteredEntries.size)
    }

    @Test
    fun testClearLogsResetsState() = runTest(testDispatcher) {
        apduLogger.logInfo("Test event 1")
        apduLogger.logInfo("Test event 2")
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(2, viewModel.uiState.value.entries.size)

        viewModel.clearLogs()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(0, viewModel.uiState.value.entries.size)
        assertEquals(0, apduLogger.getHistory().size)
    }

    @Test
    fun testToggleAutoScroll() {
        assertTrue(viewModel.uiState.value.autoScroll)
        viewModel.toggleAutoScroll()
        assertFalse(viewModel.uiState.value.autoScroll)
        viewModel.toggleAutoScroll()
        assertTrue(viewModel.uiState.value.autoScroll)
    }

    @Test
    fun testFormatLogsAsText() {
        val entry1 = ApduLogEntry(
            direction = ApduDirection.TX,
            apduHex = "00A4040000",
            description = "SELECT APPLET",
            isSuccess = true
        )
        val entry2 = ApduLogEntry(
            direction = ApduDirection.RX,
            apduHex = "9000",
            statusWordHex = "9000",
            description = "SW OK",
            durationMs = 25L,
            isSuccess = true
        )

        val formatted = viewModel.formatLogsAsText(listOf(entry1, entry2))
        assertTrue(formatted.contains("INFORME DE DIAGNÓSTICO APDU DNIe PERÚ"))
        assertTrue(formatted.contains("Total de registros: 2"))
        assertTrue(formatted.contains("[TX] SELECT APPLET"))
        assertTrue(formatted.contains("[RX] SW OK [SW: 9000] (25ms)"))
        assertTrue(formatted.contains("00A4040000"))
    }
}
