package com.whistleblower

import net.corda.core.contracts.ContractState
import net.corda.core.identity.AnonymousParty

data class BlowWhistleState(
        val badCompany: String,
        val whistleBlower: AnonymousParty,
        val investigator: AnonymousParty) : ContractState {

    override val participants = listOf(whistleBlower, investigator)
}