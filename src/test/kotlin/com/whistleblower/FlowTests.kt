package com.whistleblower

import net.corda.core.utilities.getOrThrow
import net.corda.node.internal.StartedNode
import net.corda.testing.node.MockNetwork
import net.corda.testing.node.MockNetwork.MockNode
import net.corda.testing.setCordappPackages
import net.corda.testing.unsetCordappPackages
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class FlowTests {
    private lateinit var network: MockNetwork
    private lateinit var a: StartedNode<MockNode>
    private lateinit var b: StartedNode<MockNode>
    private lateinit var c: StartedNode<MockNode>

    @Before
    fun setup() {
        setCordappPackages("com.whistleblower")
        network = MockNetwork()
        val nodes = network.createSomeNodes(3)
        a = nodes.partyNodes[0]
        b = nodes.partyNodes[1]
        c = nodes.partyNodes[2]
        nodes.partyNodes.forEach { it.registerInitiatedFlow(BlowWhistleFlowResponder::class.java) }
        network.runNetwork()
    }

    @After
    fun tearDown() {
        network.stopNodes()
        unsetCordappPackages()
    }

    @Test
    fun `flow completes successfully`() {
        val flow = BlowWhistleFlow("Enron", b.info.legalIdentities.first())
        val future = a.services.startFlow(flow).resultFuture
        network.runNetwork()
        future.getOrThrow()
    }

    // TODO: Check that the state was created in both signers' transaction storages.
    // TODO: Check that the state was created in both signers' vaults.

    @Test
    fun `in created state, neither parties are using a well-known identity`() {
        val flow = BlowWhistleFlow("Enron", b.info.legalIdentities.first())
        val future = a.services.startFlow(flow).resultFuture
        network.runNetwork()
        val stx = future.getOrThrow()

        val state = a.database.transaction {
            stx.toLedgerTransaction(a.services).outputsOfType<BlowWhistleState>().single()
        }

        val whistleBlowerWellKnownIdentities = a.info.legalIdentities.map { it.owningKey }
        val investigatorWellKnownIdentities = b.info.legalIdentities.map { it.owningKey }

        assert(state.whistleBlower.owningKey !in whistleBlowerWellKnownIdentities)
        assert(state.investigator.owningKey !in investigatorWellKnownIdentities)
    }

    @Test
    fun `parties have exchanged certs linking confidential IDs to well-known IDs`() {
        val flow = BlowWhistleFlow("Enron", b.info.legalIdentities.first())
        val future = a.services.startFlow(flow).resultFuture
        network.runNetwork()
        val stx = future.getOrThrow()

        val state = a.database.transaction {
            stx.toLedgerTransaction(a.services).outputsOfType<BlowWhistleState>().single()
        }

        a.database.transaction {
            assertNotNull(a.services.identityService.wellKnownPartyFromAnonymous(state.investigator))
        }
        b.database.transaction {
            assertNotNull(b.services.identityService.wellKnownPartyFromAnonymous(state.whistleBlower))
        }
    }

    @Test
    fun `third-party cannot link the confidential IDs to well-known IDs`() {
        val flow = BlowWhistleFlow("Enron", b.info.legalIdentities.first())
        val future = a.services.startFlow(flow).resultFuture
        network.runNetwork()
        val stx = future.getOrThrow()

        val state = a.database.transaction {
            stx.toLedgerTransaction(a.services).outputsOfType<BlowWhistleState>().single()
        }

        c.database.transaction {
            assertNull(c.services.identityService.wellKnownPartyFromAnonymous(state.investigator))
            assertNull(c.services.identityService.wellKnownPartyFromAnonymous(state.whistleBlower))
        }
    }
}