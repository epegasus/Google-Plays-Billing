package dev.epegasus.googleplaysbilling.status

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import dev.epegasus.googleplaysbilling.enums.BillingState

object State {

    private var BILLING_STATE = BillingState.NONE

    private var _billingState = MutableLiveData<BillingState>()
    val billingState: LiveData<BillingState> get() = _billingState

    fun setBillingState(billingState: BillingState) {
        BILLING_STATE = billingState
        _billingState.postValue(BILLING_STATE)
    }

    fun getBillingState(): BillingState {
        return BILLING_STATE
    }
}