![Corda](https://www.corda.net/wp-content/uploads/2016/11/fg005_corda_b.png)

# Whistle Blower CorDapp

This CorDapp is a simple showcase of confidential identities (i.e. anonymous public keys).

A node (the *whistle-blower*) can whistle-blow on a company to another node (the *investigator*). Both the 
whistle-blower and the investigator generate anonymous public keys for this transaction, meaning that any third-parties 
who manage to get ahold of the state cannot identity the whistle-blower or investigator. This process is handled 
automatically by the `SwapIdentitiesFlow`.

The investigator can then transfer an existing whistle-blowing case to another node (the *new investigator*). The 
new investigator is then informed of the identity of the whistle-blower. Again, this process is handled automatically 
by the `IdentitySyncFlow`.

# Pre-requisites:
  
See https://docs.corda.net/getting-set-up.html.

# Usage

## Running the nodes:

See https://docs.corda.net/tutorial-cordapp.html#running-the-example-cordapp.

## Interacting with the nodes:

You interact with this CorDapp using its web API. Each node exposes this web API on a different address:

* BraveEmployee: `localhost:10005/`
* TradeBody `localhost:10008/`
* GovAgency: `localhost:10011/`

The web API for each node exposes three endpoints:

* `/api/a/cases`, which lists the `BlowWhistleState`s in which the node is either the whistle-blower or current 
  investigator
* `/api/a/blow-whistle?company=X&to=Y`, which causes the node to report company X to investigator Y
* `/api/a/hand-over-investigation?caseid=X&to=Y`, which causes the existing investigator to transfer the case to 
  investigator Y
  
For example, BraveEmployee can report Enron to the TradeBody by visiting the following URL:

    http://localhost:10007/api/a/blow-whistle?company=Enron&to=TradeBody

You should see the following message:

    C=KE,L=Nairobi,O=BraveEmployee reported Enron to TradeBody.
    
If you now visit `http://localhost:10007/api/a/cases`, you should see the whistle-blowing case stored on the 
whistle-blowing node:

    [ {
      "badCompany" : "enron",
      "whistleBlower" : "8Kqd4oWdx4KQGHGKubAvzAFiUG2JjhHxM2chUs4BTHHNHnUCgf6ngCAjmCu",
      "investigator" : "8Kqd4oWdx4KQGHGGdcHPVdafymUrBvXo6KimREJhttHNhY3JVBKgTCKod1X",
      "linearId" : {
        "externalId" : null,
        "id" : "5ea06290-2dfa-4e0e-8493-a43db61404a0"
      },
      "participants" : [ "8Kqd4oWdx4KQGHGKubAvzAFiUG2JjhHxM2chUs4BTHHNHnUCgf6ngCAjmCu", "8Kqd4oWdx4KQGHGGdcHPVdafymUrBvXo6KimREJhttHNhY3JVBKgTCKod1X" ]
    } ]

We can also see the whistle-blowing case stored on the investigator node.

As we can see, the whistle-blower and investigator are identified solely by anonymous public keys. If we whistle-blow 
again:

    http://localhost:10007/api/a/blow-whistle?company=Tyco&to=TradeBody

Then when we look at the list of cases (`http://localhost:10007/api/a/cases`), we'll see that even though in both 
cases the same whistle-blower and investigator were involved, the public keys used to identify them are completely 
different, preserving their anonymity.

We can also transfer an existing case to a new investigator:

    http://localhost:10010/api/a/hand-over-investigation?caseid=[linearId]&to=GovAgency
    
We'll need to replace `[linearId]` with the actual ID of a case. We should see this message:

    C=KE,L=Kisumu,O=TradeBody handed over case 5ea06290-2dfa-4e0e-8493-a43db61404a0 to GovAgency.
    
And again, if we visit the list of cases (`http://localhost:10013/api/a/cases`), we'll see that the new investigator 
(as well as the whistle-blower) are identified solely using an anonymous public key!