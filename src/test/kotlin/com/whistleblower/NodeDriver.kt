package com.whistleblower

import net.corda.core.identity.CordaX500Name
import net.corda.core.utilities.getOrThrow
import net.corda.node.services.transactions.ValidatingNotaryService
import net.corda.nodeapi.User
import net.corda.nodeapi.internal.ServiceInfo
import net.corda.testing.driver.driver

/**
 * This file is exclusively for being able to run your nodes through an IDE (as opposed to using deployNodes)
 * Do not use in a production environment.
 */
fun main(args: Array<String>) {
    // No permissions required as we are not invoking flows.
    val user = User("user1", "test", permissions = setOf())
    driver(isDebug = true, startNodesInProcess = true) {
        startNode(providedName = CordaX500Name("Controller", "Nakuru", "KE"), advertisedServices = setOf(ServiceInfo(ValidatingNotaryService.type)))
        val (nodeA, nodeB, nodeC) = listOf(
                startNode(providedName = CordaX500Name("BraveEmployee", "Nairobi", "KE"), rpcUsers = listOf(user)).getOrThrow(),
                startNode(providedName = CordaX500Name("TradeBody", "Kisumu", "KE"), rpcUsers = listOf(user)).getOrThrow(),
                startNode(providedName = CordaX500Name("GovAgency", "Mombasa", "KE"), rpcUsers = listOf(user)).getOrThrow())

        startWebserver(nodeA)
        startWebserver(nodeB)
        startWebserver(nodeC)

        waitForAllNodesToFinish()
    }
}