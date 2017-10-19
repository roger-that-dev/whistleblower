package com.whistleblower

import co.paralleluniverse.fibers.Suspendable
import com.whistleblower.BlowWhistleContract.Commands.HandOverInvestigationCmd
import net.corda.confidential.IdentitySyncFlow
import net.corda.confidential.SwapIdentitiesFlow
import net.corda.core.contracts.Command
import net.corda.core.contracts.StateAndRef
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.contracts.requireThat
import net.corda.core.flows.*
import net.corda.core.identity.AnonymousParty
import net.corda.core.identity.Party
import net.corda.core.node.services.queryBy
import net.corda.core.node.services.vault.QueryCriteria
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.ProgressTracker
import net.corda.core.utilities.ProgressTracker.Step

@InitiatingFlow
@StartableByRPC
class HandOverInvestigationFlow(private val caseID: UniqueIdentifier, private val newInvestigator: Party) : FlowLogic<SignedTransaction>() {

    companion object {
        object RETRIEVE_STATE : Step("Retrieving the current state from the vault.")
        object GENERATE_CONFIDENTIAL_IDS : Step("Generating confidential identities for the transaction.") {
            override fun childProgressTracker() = SwapIdentitiesFlow.tracker()
        }

        object BUILD_TRANSACTION : Step("Building the transaction.")
        object VERIFY_TRANSACTION : Step("Verifying the transaction.")
        object SIGN_TRANSACTION : Step("I sign the transaction.")
        object SYNC_CONFIDENTIAL_IDS : Step("Exchange certificates for relevant confidential identities.") {
            override fun childProgressTracker() = IdentitySyncFlow.Send.tracker()
        }

        object COLLECT_COUNTERPARTY_SIG : Step("The counterparty signs the transaction.") {
            override fun childProgressTracker() = CollectSignaturesFlow.tracker()
        }

        object FINALISE_TRANSACTION : Step("Obtaining notary signature and recording transaction.") {
            override fun childProgressTracker() = FinalityFlow.tracker()
        }

        fun tracker() = ProgressTracker(
                RETRIEVE_STATE,
                GENERATE_CONFIDENTIAL_IDS,
                BUILD_TRANSACTION,
                VERIFY_TRANSACTION,
                SIGN_TRANSACTION,
                SYNC_CONFIDENTIAL_IDS,
                COLLECT_COUNTERPARTY_SIG,
                FINALISE_TRANSACTION
        )
    }

    override val progressTracker = tracker()

    @Suspendable
    override fun call(): SignedTransaction {
        progressTracker.currentStep = RETRIEVE_STATE
        val (oldBlowWhistleStateAndRef, oldBlowWhistleState) = retrieveBlowWhistleStateByID()

        progressTracker.currentStep = GENERATE_CONFIDENTIAL_IDS
        val (anonymousMe, anonymousNewInvestigator) = generateConfidentialIdentities(oldBlowWhistleState)

        progressTracker.currentStep = BUILD_TRANSACTION
        val newBlowWhistleState = oldBlowWhistleState.copy(investigator = anonymousNewInvestigator)
        val command = Command(HandOverInvestigationCmd(), listOf(anonymousMe.owningKey, anonymousNewInvestigator.owningKey))
        val txBuilder = TransactionBuilder(serviceHub.networkMapCache.notaryIdentities.first())
                .addInputState(oldBlowWhistleStateAndRef)
                .addOutputState(newBlowWhistleState, BLOW_WHISTLE_CONTRACT_ID)
                .addCommand(command)

        progressTracker.currentStep = VERIFY_TRANSACTION
        txBuilder.verify(serviceHub)

        progressTracker.currentStep = SIGN_TRANSACTION
        val stx = serviceHub.signInitialTransaction(txBuilder, anonymousMe.owningKey)

        progressTracker.currentStep = SYNC_CONFIDENTIAL_IDS
        val newInvestigatorSession = initiateFlow(newInvestigator)
        subFlow(IdentitySyncFlow.Send(
                setOf(newInvestigatorSession),
                stx.tx,
                SYNC_CONFIDENTIAL_IDS.childProgressTracker()))

        progressTracker.currentStep = COLLECT_COUNTERPARTY_SIG
        val ftx = subFlow(CollectSignaturesFlow(
                stx,
                listOf(newInvestigatorSession),
                listOf(anonymousMe.owningKey),
                COLLECT_COUNTERPARTY_SIG.childProgressTracker()))

        progressTracker.currentStep = FINALISE_TRANSACTION
        return subFlow(FinalityFlow(ftx, FINALISE_TRANSACTION.childProgressTracker()))
    }

    @Suspendable
    private fun retrieveBlowWhistleStateByID(): Pair<StateAndRef<BlowWhistleState>, BlowWhistleState> {
        val criteria = QueryCriteria.LinearStateQueryCriteria(linearId = listOf(caseID))
        val oldBlowWhistleStateAndRef = serviceHub.vaultService.queryBy<BlowWhistleState>(criteria).states.singleOrNull()
                ?: throw FlowException("The case ID did not return a single BlowWhistleState.")
        val oldBlowWhistleState = oldBlowWhistleStateAndRef.state.data
        return oldBlowWhistleStateAndRef to oldBlowWhistleState
    }

    @Suspendable
    private fun generateConfidentialIdentities(oldBlowWhistleState: BlowWhistleState): Pair<AnonymousParty, AnonymousParty> {
        val confidentialIdentities = subFlow(SwapIdentitiesFlow(
                newInvestigator,
                false,
                GENERATE_CONFIDENTIAL_IDS.childProgressTracker()))
        val anonymousMe = oldBlowWhistleState.investigator
        val anonymousNewInvestigator = confidentialIdentities[newInvestigator]
                ?: throw IllegalArgumentException("Could not anonymise investigator's identity.")
        return anonymousMe to anonymousNewInvestigator
    }
}

@InitiatedBy(HandOverInvestigationFlow::class)
class HandOverInvestigationFlowResponder(val counterpartySession: FlowSession) : FlowLogic<Unit>() {
    @Suspendable
    override fun call() {
        subFlow(IdentitySyncFlow.Receive(counterpartySession))

        val signTransactionFlow = object : SignTransactionFlow(counterpartySession) {
            override fun checkTransaction(stx: SignedTransaction) = requireThat {
                // TODO: Checking.
            }
        }

        subFlow(signTransactionFlow)
    }
}