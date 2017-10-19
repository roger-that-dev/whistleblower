package com.whistleblower

import net.corda.core.contracts.LinearState
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.identity.AnonymousParty

data class BlowWhistleState(
        val badCompany: String,
        val whistleBlower: AnonymousParty,
        val investigator: AnonymousParty,
        override val linearId: UniqueIdentifier = UniqueIdentifier()) : LinearState {

    override val participants = listOf(whistleBlower, investigator)
}