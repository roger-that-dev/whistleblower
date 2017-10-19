package com.whistleblower.contract

import com.whistleblower.BLOW_WHISTLE_CONTRACT_ID
import com.whistleblower.BlowWhistleContract
import com.whistleblower.BlowWhistleContract.Commands.HandOverInvestigationCmd
import com.whistleblower.BlowWhistleState
import net.corda.testing.*
import net.corda.testing.contracts.DummyState
import org.junit.After
import org.junit.Before
import org.junit.Test

class HandOverInvestigationTests {
    private val whistleBlower = MINI_CORP.anonymise()
    private val oldInvestigator = MEGA_CORP.anonymise()
    private val newInvestigator = BIG_CORP.anonymise()

    private val validInput = BlowWhistleState("Enron", whistleBlower, oldInvestigator)
    private val validOutput = BlowWhistleState("Enron", whistleBlower, newInvestigator, validInput.linearId)

    @Before
    fun setup() {
        setCordappPackages("com.whistleblower")
    }

    @After
    fun tearDown() {
        unsetCordappPackages()
    }

    @Test
    fun `A HandOverInvestigation transaction should have a single BlowWhistleState input and a single BlowWhistleState output`() {
        // No input state.
        ledger {
            transaction {
                output(BLOW_WHISTLE_CONTRACT_ID) { validOutput }
                command(oldInvestigator.owningKey, newInvestigator.owningKey) { HandOverInvestigationCmd() }
                fails()
            }
        }
        // No output state.
        ledger {
            transaction {
                input(BLOW_WHISTLE_CONTRACT_ID) { validInput }
                command(oldInvestigator.owningKey, newInvestigator.owningKey) { HandOverInvestigationCmd() }
                fails()
            }
        }
        // Two input states.
        ledger {
            transaction {
                input(BLOW_WHISTLE_CONTRACT_ID) { validInput }
                input(BLOW_WHISTLE_CONTRACT_ID) { validInput }
                output(BLOW_WHISTLE_CONTRACT_ID) { validOutput }
                command(oldInvestigator.owningKey, newInvestigator.owningKey) { HandOverInvestigationCmd() }
                fails()
            }
        }
        // Two output states.
        ledger {
            transaction {
                input(BLOW_WHISTLE_CONTRACT_ID) { validInput }
                output(BLOW_WHISTLE_CONTRACT_ID) { validOutput }
                output(BLOW_WHISTLE_CONTRACT_ID) { validOutput }
                command(oldInvestigator.owningKey, newInvestigator.owningKey) { HandOverInvestigationCmd() }
                fails()
            }
        }
        // Wrong input state.
        ledger {
            transaction {
                input(BLOW_WHISTLE_CONTRACT_ID) { DummyState(0) }
                output(BLOW_WHISTLE_CONTRACT_ID) { validOutput }
                command(oldInvestigator.owningKey, newInvestigator.owningKey) { HandOverInvestigationCmd() }
                fails()
            }
        }
        // Wrong output state.
        ledger {
            transaction {
                input(BLOW_WHISTLE_CONTRACT_ID) { validInput }
                output(BLOW_WHISTLE_CONTRACT_ID) { DummyState(0) }
                command(oldInvestigator.owningKey, newInvestigator.owningKey) { HandOverInvestigationCmd() }
                fails()
            }
        }

        ledger {
            transaction {
                input(BLOW_WHISTLE_CONTRACT_ID) { validInput }
                output(BLOW_WHISTLE_CONTRACT_ID) { validOutput }
                command(oldInvestigator.owningKey, newInvestigator.owningKey) { HandOverInvestigationCmd() }
                verifies()
            }
        }
    }

    @Test
    fun `A HandOverInvestigation transaction should be signed by the old investigator and the new investigator`() {
        // No old investigator signature.
        ledger {
            transaction {
                input(BLOW_WHISTLE_CONTRACT_ID) { validInput }
                output(BLOW_WHISTLE_CONTRACT_ID) { validOutput }
                command(newInvestigator.owningKey) { HandOverInvestigationCmd() }
                fails()
            }
        }
        // No new investigator signature.
        ledger {
            transaction {
                input(BLOW_WHISTLE_CONTRACT_ID) { validInput }
                output(BLOW_WHISTLE_CONTRACT_ID) { validOutput }
                command(oldInvestigator.owningKey) { HandOverInvestigationCmd() }
                fails()
            }
        }

        ledger {
            transaction {
                input(BLOW_WHISTLE_CONTRACT_ID) { validInput }
                output(BLOW_WHISTLE_CONTRACT_ID) { validOutput }
                command(newInvestigator.owningKey, oldInvestigator.owningKey) { HandOverInvestigationCmd() }
                verifies()
            }
        }
    }

    @Test
    fun `A HandOverInvestigation transaction should only change the investigator`() {
        // Change in the company being named.
        ledger {
            transaction {
                input(BLOW_WHISTLE_CONTRACT_ID) { validInput }
                output(BLOW_WHISTLE_CONTRACT_ID) { validOutput.copy(badCompany = "x") }
                command(newInvestigator.owningKey, oldInvestigator.owningKey) { HandOverInvestigationCmd() }
                fails()
            }
        }
        // Change in the whistle-blower.
        ledger {
            transaction {
                input(BLOW_WHISTLE_CONTRACT_ID) { validInput }
                output(BLOW_WHISTLE_CONTRACT_ID) { validOutput.copy(whistleBlower = oldInvestigator) }
                command(newInvestigator.owningKey, oldInvestigator.owningKey) { HandOverInvestigationCmd() }
                fails()
            }
        }

        ledger {
            transaction {
                input(BLOW_WHISTLE_CONTRACT_ID) { validInput }
                output(BLOW_WHISTLE_CONTRACT_ID) { validOutput }
                command(newInvestigator.owningKey, oldInvestigator.owningKey) { HandOverInvestigationCmd() }
                verifies()
            }
        }
    }
}