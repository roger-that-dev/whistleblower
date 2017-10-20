package com.whistleblower

import net.corda.core.contracts.CommandData
import net.corda.core.contracts.Contract
import net.corda.core.contracts.requireSingleCommand
import net.corda.core.contracts.requireThat
import net.corda.core.transactions.LedgerTransaction

val BLOW_WHISTLE_CONTRACT_ID = "com.whistleblower.BlowWhistleContract"

/**
 * A contract supporting two state transitions:
 * - Blowing the whistle on a company
 * - Transferring an existing case to a new investigator
 */
open class BlowWhistleContract : Contract {
    override fun verify(tx: LedgerTransaction) {
        val cmd = tx.commands.requireSingleCommand<Commands>()
        when (cmd.value) {
            is Commands.BlowWhistleCmd -> requireThat {
                "A BlowWhistle transaction should have zero inputs." using (tx.inputs.isEmpty())
                "A BlowWhistle transaction should have a BlowWhistleState output." using (tx.outputsOfType<BlowWhistleState>().size == 1)
                "A BlowWhistle transaction should have no other outputs." using (tx.outputs.size == 1)

                val output = tx.outputsOfType<BlowWhistleState>().single()
                "A BlowWhistle transaction should be signed by the whistle-blower and the investigator." using
                        (cmd.signers.containsAll(output.participants.map { it.owningKey }))
            }
            is Commands.HandOverInvestigationCmd -> requireThat {
                "A HandOverInvestigation transaction should have a BlowWhistleState input." using
                        (tx.inputsOfType<BlowWhistleState>().size == 1)
                "A HandOverInvestigation transaction should have no other inputs." using (tx.inputs.size == 1)
                "A HandOverInvestigation transaction should have a BlowWhistleState output." using
                        (tx.outputsOfType<BlowWhistleState>().size == 1)
                "A HandOverInvestigation transaction should have no other outputs." using (tx.outputs.size == 1)

                val input = tx.inputsOfType<BlowWhistleState>().single()
                val output = tx.outputsOfType<BlowWhistleState>().single()
                "A HandOverInvestigation should only update the investigator field." using
                        (input.copy(investigator = output.investigator) == output)

                val oldInvestigator = tx.inputsOfType<BlowWhistleState>().single().investigator
                val newInvestigator = tx.outputsOfType<BlowWhistleState>().single().investigator
                "A HandOverInvestigation transaction should be signed by the old investigator and the new investigator." using
                        (cmd.signers.containsAll(listOf(oldInvestigator.owningKey, newInvestigator.owningKey)))
            }
        }
    }

    sealed class Commands : CommandData {
        /** Blowing the whistle on a company. */
        class BlowWhistleCmd : Commands()
        /** Transferring an existing case to a new investigator. */
        class HandOverInvestigationCmd : Commands()
    }
}