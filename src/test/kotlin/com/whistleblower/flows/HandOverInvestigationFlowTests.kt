package com.whistleblower.flows

import com.whistleblower.BlowWhistleState
import net.corda.core.node.services.queryBy
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class HandOverInvestigationFlowTests : FlowTestsBase() {

    @Test
    fun `flow completes successfully`() {
        handOverInvestigation()
    }

    @Test
    fun `both parties recorded transaction and state`() {
        val stx = handOverInvestigation()

        listOf(firstInvestigator, secondInvestigator).forEach { node ->
            val recordedTx = node.services.validatedTransactions.getTransaction(stx.id)
            assertNotNull(recordedTx)
            val recordedStates = node.database.transaction {
                node.services.vaultService.queryBy<BlowWhistleState>().states
            }
            if (node == firstInvestigator) {
                assertEquals(0, recordedStates.size)
            } else {
                assertEquals(1, recordedStates.size)
                assertEquals(stx.id, recordedStates.single().ref.txhash)
            }
        }
    }

    @Test
    fun `in created state, neither party is using a well-known identity`() {
        val stx = handOverInvestigation()

        val state = firstInvestigator.database.transaction {
            stx.toLedgerTransaction(firstInvestigator.services).outputsOfType<BlowWhistleState>().single()
        }

        assert(state.whistleBlower.owningKey !in firstInvestigator.legalIdentityKeys)
        assert(state.investigator.owningKey !in secondInvestigator.legalIdentityKeys)
    }

    @Test
    fun `old and new investigator have exchanged certs linking confidential IDs to well-known IDs`() {
        val stx = handOverInvestigation()

        val (input, output) = firstInvestigator.database.transaction {
            val ledgerTx = stx.toLedgerTransaction(firstInvestigator.services)
            ledgerTx.inputsOfType<BlowWhistleState>().single() to ledgerTx.outputsOfType<BlowWhistleState>().single()
        }

        firstInvestigator.database.transaction {
            assertNotNull(firstInvestigator.partyFromAnonymous(output.investigator))
        }
        secondInvestigator.database.transaction {
            listOf(input.whistleBlower, input.investigator, output.investigator).forEach {
                assertNotNull(secondInvestigator.partyFromAnonymous(it))
            }
        }
    }

    @Test
    fun `whistle-blower and new investigator have not exchanged certs linking confidential IDs to well-known IDs`() {
        val stx = handOverInvestigation()

        val output = firstInvestigator.database.transaction {
            val ledgerTx = stx.toLedgerTransaction(firstInvestigator.services)
            ledgerTx.outputsOfType<BlowWhistleState>().single()
        }

        whistleBlower.database.transaction {
            assertNull(whistleBlower.partyFromAnonymous(output.investigator))
        }
    }

    @Test
    fun `third-party cannot link the confidential IDs to well-known IDs`() {
        val stx = handOverInvestigation()

        val (input, output) = firstInvestigator.database.transaction {
            val ledgerTx = stx.toLedgerTransaction(firstInvestigator.services)
            ledgerTx.inputsOfType<BlowWhistleState>().single() to ledgerTx.outputsOfType<BlowWhistleState>().single()
        }

        thirdParty.database.transaction {
            listOf(input.whistleBlower, input.investigator, output.investigator).forEach {
                assertNull(thirdParty.partyFromAnonymous(it))
            }
        }
    }
}