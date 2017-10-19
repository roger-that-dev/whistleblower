package com.whistleblower.contract

import com.whistleblower.BLOW_WHISTLE_CONTRACT_ID
import com.whistleblower.BlowWhistleContract.Commands.BlowWhistleCmd
import com.whistleblower.BlowWhistleContract.Commands.HandOverInvestigationCmd
import com.whistleblower.BlowWhistleState
import net.corda.testing.*
import net.corda.testing.contracts.DummyState
import org.junit.After
import org.junit.Before
import org.junit.Test

class BlowWhistleTests {
    private val whistleBlower = MINI_CORP.anonymise()
    private val investigator = MEGA_CORP.anonymise()

    @Before
    fun setup() {
        setCordappPackages("com.whistleblower")
    }

    @After
    fun tearDown() {
        unsetCordappPackages()
    }

    @Test
    fun `A BlowWhistleState transaction must have a BlowWhistleContract command`() {
        // Wrong command type.
        ledger {
            transaction {
                output(BLOW_WHISTLE_CONTRACT_ID) { BlowWhistleState("Enron", whistleBlower, investigator) }
                command(whistleBlower.owningKey, investigator.owningKey) { DummyCommandData }
                fails()
            }
        }

        ledger {
            transaction {
                output(BLOW_WHISTLE_CONTRACT_ID) { BlowWhistleState("Enron", whistleBlower, investigator) }
                command(whistleBlower.owningKey, investigator.owningKey) { BlowWhistleCmd() }
                verifies()
            }
        }

        ledger {
            transaction {
                output(BLOW_WHISTLE_CONTRACT_ID) { BlowWhistleState("Enron", whistleBlower, investigator) }
                command(whistleBlower.owningKey, investigator.owningKey) { HandOverInvestigationCmd() }
                verifies()
            }
        }
    }

    @Test
    fun `A BlowWhistle transaction should have zero inputs and a single BlowWhistleState output`() {
        // Input state.
        ledger {
            transaction {
                input(BLOW_WHISTLE_CONTRACT_ID) { BlowWhistleState("Enron", whistleBlower, investigator) }
                output(BLOW_WHISTLE_CONTRACT_ID) { BlowWhistleState("Enron", whistleBlower, investigator) }
                command(whistleBlower.owningKey, investigator.owningKey) { BlowWhistleCmd() }
                fails()
            }
        }
        // Wrong output state type.
        ledger {
            transaction {
                output(BLOW_WHISTLE_CONTRACT_ID) { DummyState(0) }
                command(whistleBlower.owningKey, investigator.owningKey) { BlowWhistleCmd() }
                fails()
            }
        }
        // Two output states.
        ledger {
            transaction {
                output(BLOW_WHISTLE_CONTRACT_ID) { BlowWhistleState("Enron", whistleBlower, investigator) }
                output(BLOW_WHISTLE_CONTRACT_ID) { BlowWhistleState("Enron", whistleBlower, investigator) }
                command(whistleBlower.owningKey, investigator.owningKey) { BlowWhistleCmd() }
                fails()
            }
        }

        ledger {
            transaction {
                output(BLOW_WHISTLE_CONTRACT_ID) { BlowWhistleState("Enron", whistleBlower, investigator) }
                command(whistleBlower.owningKey, investigator.owningKey) { BlowWhistleCmd() }
                verifies()
            }
        }
    }

    @Test
    fun `A BlowWhistle transaction should be signed by the whistle-blower and the investigator`() {
        // No whistle-blower signature.
        ledger {
            transaction {
                output(BLOW_WHISTLE_CONTRACT_ID) { BlowWhistleState("Enron", whistleBlower, investigator) }
                command(investigator.owningKey) { BlowWhistleCmd() }
                fails()
            }
        }
        // No investigator signature.
        ledger {
            transaction {
                output(BLOW_WHISTLE_CONTRACT_ID) { BlowWhistleState("Enron", whistleBlower, investigator) }
                command(whistleBlower.owningKey) { BlowWhistleCmd() }
                fails()
            }
        }

        ledger {
            transaction {
                output(BLOW_WHISTLE_CONTRACT_ID) { BlowWhistleState("Enron", whistleBlower, investigator) }
                command(whistleBlower.owningKey, investigator.owningKey) { BlowWhistleCmd() }
                verifies()
            }
        }
    }
}