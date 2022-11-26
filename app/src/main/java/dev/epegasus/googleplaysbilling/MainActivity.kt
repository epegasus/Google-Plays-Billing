package dev.epegasus.googleplaysbilling

import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import dev.epegasus.billinginapppurchases.BillingManager
import dev.epegasus.billinginapppurchases.helper.BillingHelper.Companion.TAG
import dev.epegasus.googleplaysbilling.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private val binding by lazy { ActivityMainBinding.inflate(layoutInflater) }
    private val billingManager by lazy { BillingManager(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)

        initBilling()
        initObserver()

        binding.mbMakePurchase.setOnClickListener { onPurchaseClick() }
    }

    private fun initObserver() {
        dev.epegasus.billinginapppurchases.status.State.billingState.observe(this) {
            Log.d("BillingManager", "initObserver: $it")
            binding.tvTitle.text = it.toString()
        }
    }

    private fun initBilling() {
        if (BuildConfig.DEBUG) {
            billingManager.startConnection(billingManager.getDebugProductIDList()) { connectionResult, message ->
                Log.d(TAG, "initBilling: $connectionResult")
                runOnUiThread {
                    binding.mbMakePurchase.isEnabled = true
                    Toast.makeText(this, message, Toast.LENGTH_LONG).show()
                }
            }
        } else {
            billingManager.startConnection(listOf(packageName)) { connectionResult, message ->
                binding.mbMakePurchase.isEnabled = connectionResult
                Toast.makeText(this, message, Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun onPurchaseClick() {
        billingManager.makePurchase { isSuccess, message ->
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
            binding.mbMakePurchase.isEnabled = !isSuccess
        }
    }
}