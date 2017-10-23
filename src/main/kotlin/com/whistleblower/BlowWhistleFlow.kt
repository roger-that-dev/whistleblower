package com.whistleblower

import co.paralleluniverse.fibers.Suspendable
import com.whistleblower.BlowWhistleContract.Commands.BlowWhistleCmd
import net.corda.confidential.SwapIdentitiesFlow
import net.corda.core.contracts.Command
import net.corda.core.contracts.requireThat
import net.corda.core.flows.*
import net.corda.core.identity.AnonymousParty
import net.corda.core.identity.Party
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.ProgressTracker
import net.corda.core.utilities.ProgressTracker.Step

/**
 * Blows the whistle on a company.
 *
 * Confidential identities are used to preserve the identity of the whistle-blower and the investigator.
 *
 * @param badCompany the company the whistle is being blown on.
 * @param investigator the party handling the investigation.
 */
@InitiatingFlow
@StartableByRPC
class BlowWhistleFlow(private val badCompany: String, private val investigator: Party) : FlowLogic<SignedTransaction>() {

    companion object {
        object GENERATE_CONFIDENTIAL_IDS : Step("Generating confidential identities for the transaction.") {
            override fun childProgressTracker() = SwapIdentitiesFlow.tracker()
        }

        object BUILD_TRANSACTION : Step("Building the transaction.")
        object VERIFY_TRANSACTION : Step("Verifying the transaction.")
        object SIGN_TRANSACTION : Step("I sign the transaction.")
        object COLLECT_COUNTERPARTY_SIG : Step("The counterparty signs the transaction.") {
            override fun childProgressTracker() = CollectSignaturesFlow.tracker()
        }

        object FINALISE_TRANSACTION : Step("Obtaining notary signature and recording transaction.") {
            override fun childProgressTracker() = FinalityFlow.tracker()
        }

        fun tracker() = ProgressTracker(
                GENERATE_CONFIDENTIAL_IDS,
                BUILD_TRANSACTION,
                VERIFY_TRANSACTION,
                SIGN_TRANSACTION,
                COLLECT_COUNTERPARTY_SIG,
                FINALISE_TRANSACTION
        )
    }

    override val progressTracker = tracker()

    @Suspendable
    override fun call(): SignedTransaction {
        progressTracker.currentStep = GENERATE_CONFIDENTIAL_IDS
        val (anonymousMe, anonymousInvestigator) = generateConfidentialIdentities()

        progressTracker.currentStep = BUILD_TRANSACTION
        val output = BlowWhistleState(badCompany, anonymousMe, anonymousInvestigator)
        val command = Command(BlowWhistleCmd(), listOf(anonymousMe.owningKey, anonymousInvestigator.owningKey))
        val txBuilder = TransactionBuilder(serviceHub.networkMapCache.notaryIdentities.first())
                .addOutputState(output, BLOW_WHISTLE_CONTRACT_ID)
                .addCommand(command)

        progressTracker.currentStep = VERIFY_TRANSACTION
        txBuilder.verify(serviceHub)

        progressTracker.currentStep = SIGN_TRANSACTION
        val stx = serviceHub.signInitialTransaction(txBuilder, anonymousMe.owningKey)

        progressTracker.currentStep = COLLECT_COUNTERPARTY_SIG
        val investigatorSession = initiateFlow(investigator)
        val ftx = subFlow(CollectSignaturesFlow(        // Use parameter names here.
                stx,
                listOf(investigatorSession),
                listOf(anonymousMe.owningKey),
                COLLECT_COUNTERPARTY_SIG.childProgressTracker()))

        progressTracker.currentStep = FINALISE_TRANSACTION
        return subFlow(FinalityFlow(ftx, FINALISE_TRANSACTION.childProgressTracker()))
    }

    /** Generates confidential identities for the whistle-blower and the investigator. */
    @Suspendable
    private fun generateConfidentialIdentities(): Pair<AnonymousParty, AnonymousParty> {
        val confidentialIdentities = subFlow(SwapIdentitiesFlow( // Add parameter names here to make it a bit clearer.
                investigator,
                false,
                GENERATE_CONFIDENTIAL_IDS.childProgressTracker()))
        val anonymousMe = confidentialIdentities[ourIdentity]
                ?: throw IllegalArgumentException("Could not anonymise my identity.")
        val anonymousInvestigator = confidentialIdentities[investigator]
                ?: throw IllegalArgumentException("Could not anonymise investigator's identity.")
        return anonymousMe to anonymousInvestigator // Maybe just use the Pair(a, b) syntax here?
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
