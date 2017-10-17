package com.whistleblower

import co.paralleluniverse.fibers.Suspendable
import com.whistleblower.BlowWhistleContract.Commands.BlowWhistleCmd
import net.corda.confidential.SwapIdentitiesFlow
import net.corda.core.contracts.Command
import net.corda.core.contracts.requireThat
import net.corda.core.flows.*
import net.corda.core.identity.Party
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder

@InitiatingFlow
@StartableByRPC
class BlowWhistleFlow(private val badCompany: String, private val investigator: Party) : FlowLogic<SignedTransaction>() {
    @Suspendable
    override fun call(): SignedTransaction {
        val txIdentities = subFlow(SwapIdentitiesFlow(investigator))
        val anonymousMe = txIdentities[ourIdentity]
                ?: throw IllegalArgumentException("Could not anonymise my identity.")
        val anonymousInvestigator = txIdentities[investigator]
                ?: throw IllegalArgumentException("Could not anonymise investigator's identity.")

        val output = BlowWhistleState(badCompany, anonymousMe, anonymousInvestigator)
        val command = Command(BlowWhistleCmd(), listOf(anonymousMe.owningKey, anonymousInvestigator.owningKey))

        val notary = serviceHub.networkMapCache.notaryIdentities.first()
        val txBuilder = TransactionBuilder(notary)
                .addOutputState(output, BLOW_WHISTLE_CONTRACT_ID)
                .addCommand(command)

        val stx = serviceHub.signInitialTransaction(txBuilder, anonymousMe.owningKey)

        val investigatorSession = initiateFlow(investigator)
        val ftx = subFlow(CollectSignaturesFlow(stx, listOf(investigatorSession), listOf(anonymousMe.owningKey)))

        return subFlow(FinalityFlow(ftx))
    }
}

@InitiatedBy(BlowWhistleFlow::class)
class BlowWhistleFlowResponder(val counterpartySession: FlowSession) : FlowLogic<Unit>() {
    @Suspendable
    override fun call() {
        val signTransactionFlow = object : SignTransactionFlow(counterpartySession) {
            override fun checkTransaction(stx: SignedTransaction) = requireThat {
                // TODO: Checking.
            }
        }

        subFlow(signTransactionFlow)
    }
}