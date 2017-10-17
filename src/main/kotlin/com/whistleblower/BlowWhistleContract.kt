package com.whistleblower

import net.corda.core.contracts.CommandData
import net.corda.core.contracts.Contract
import net.corda.core.contracts.requireSingleCommand
import net.corda.core.contracts.requireThat
import net.corda.core.transactions.LedgerTransaction

val BLOW_WHISTLE_CONTRACT_ID = "com.whistleblower.BlowWhistleContract"

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
                "A BlowWhistle transaction should have a BlowWhistleState input." using (tx.outputsOfType<BlowWhistleState>().size == 1)
                "A BlowWhistle transaction should have no other inputs." using (tx.inputs.size == 1)
                "A BlowWhistle transaction should have a BlowWhistleState output." using (tx.outputsOfType<BlowWhistleState>().size == 1)
                "A BlowWhistle transaction should have no other outputs." using (tx.outputs.size == 1)

                val oldInvestigator = tx.inputsOfType<BlowWhistleState>().single().investigator
                val newInvestigator = tx.outputsOfType<BlowWhistleState>().single().investigator
                "A HandOverInvestigation transaction should be signed by the old investigator and the new investigator." using
                        (cmd.signers.containsAll(listOf(oldInvestigator.owningKey, newInvestigator.owningKey)))
            }
        }
    }

    // Used to indicate the transaction's intent.
    sealed class Commands : CommandData {
        class BlowWhistleCmd : Commands()
        class HandOverInvestigationCmd : Commands()
    }
}