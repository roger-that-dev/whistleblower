package com.whistleblower.flows

import com.whistleblower.*
import net.corda.core.identity.AnonymousParty
import net.corda.core.identity.Party
import net.corda.core.internal.declaredField
import net.corda.core.transactions.SignedTransaction
import net.corda.core.utilities.getOrThrow
import net.corda.node.internal.StartedNode
import net.corda.node.services.api.ServiceHubInternal
import net.corda.testing.node.MockNetwork
import net.corda.testing.node.MockNetwork.MockNode
import net.corda.testing.setCordappPackages
import net.corda.testing.unsetCordappPackages
import org.junit.After
import org.junit.Before
import java.security.PublicKey

abstract class FlowTestsBase {
    private lateinit var network: MockNetwork
    protected lateinit var whistleBlower: StartedNode<MockNode>
    protected lateinit var firstInvestigator: StartedNode<MockNode>
    protected lateinit var secondInvestigator: StartedNode<MockNode>
    protected lateinit var thirdParty: StartedNode<MockNode>

    @Before
    fun setup() {
        setCordappPackages("com.whistleblower")
        network = MockNetwork()
        val nodes = network.createSomeNodes(4)
        whistleBlower = nodes.partyNodes[0]
        firstInvestigator = nodes.partyNodes[1]
        secondInvestigator = nodes.partyNodes[2]
        thirdParty = nodes.partyNodes[3]
        nodes.partyNodes.forEach {
            it.registerInitiatedFlow(BlowWhistleFlowResponder::class.java)
            it.registerInitiatedFlow(HandOverInvestigationFlowResponder::class.java)
        }

        network.runNetwork()
    }

    @After
    fun tearDown() {
        network.stopNodes()
        unsetCordappPackages()
    }

    protected fun blowWhistle(): SignedTransaction {
        val flow = BlowWhistleFlow("Enron", firstInvestigator.info.legalIdentities.first())
        val future = whistleBlower.services.startFlow(flow).resultFuture
        network.runNetwork()
        return future.getOrThrow()
    }

    protected fun handOverInvestigation(): SignedTransaction {
        val stx = blowWhistle()
        val caseID = firstInvestigator.database.transaction {
            stx.tx.outputsOfType<BlowWhistleState>().single().linearId
        }

        val flow = HandOverInvestigationFlow(caseID, secondInvestigator.info.legalIdentities.first())
        val future = firstInvestigator.services.startFlow(flow).resultFuture
        network.runNetwork()
        return future.getOrThrow()
    }

    protected fun StartedNode<MockNode>.partyFromAnonymous(anonParty: AnonymousParty): Party? {
        val services = this.declaredField<ServiceHubInternal>("services").value
        return services.identityService.wellKnownPartyFromAnonymous(anonParty)
    }

    val StartedNode<MockNode>.legalIdentityKeys: List<PublicKey>
        get() = info.legalIdentities.map { it.owningKey }
}