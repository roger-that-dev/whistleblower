package com.whistleblower

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.flows.*

@InitiatingFlow
@StartableByRPC
class HandOverInvestigationFlow : FlowLogic<Unit>() {
    @Suspendable
    override fun call() {
        return Unit
    }
}

@InitiatedBy(HandOverInvestigationFlow::class)
class HandOverInvestigationFlowResponder(val counterpartySession: FlowSession) : FlowLogic<Unit>() {
    @Suspendable
    override fun call() {
        return Unit
    }
}