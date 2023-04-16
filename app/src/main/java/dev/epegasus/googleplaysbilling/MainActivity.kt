package dev.epegasus.googleplaysbilling

import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import dev.epegasus.billinginapppurchases.BillingManager
import dev.epegasus.billinginapppurchases.enums.SubscriptionTags
import dev.epegasus.billinginapppurchases.interfaces.OnConnectionListener
import dev.epegasus.billinginapppurchases.interfaces.OnPurchaseListener
import dev.epegasus.billinginapppurchases.status.State
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
        State.billingState.observe(this) {
            Log.d("BillingManager", "initObserver: $it")
            binding.tvTitle.text = it.toString()
        }
    }

    private fun initBilling() {
        billingManager.setCheckForSubscription(true)
        if (BuildConfig.DEBUG) {
            billingManager.startConnection(billingManager.getDebugProductIDList(), object : OnConnectionListener {
                override fun onConnectionResult(isSuccess: Boolean, message: String) {
                    binding.mbMakePurchase.isEnabled = isSuccess
                    Log.d("TAG", "onConnectionResult: $isSuccess - $message")
                }

                override fun onOldPurchaseResult(isPurchased: Boolean) {
                    // Update your shared-preferences here!
                    Log.d("TAG", "onOldPurchaseResult: $isPurchased")
                }
            })
        } else {
            billingManager.startConnection(listOf(packageName), object : OnConnectionListener {
                override fun onConnectionResult(isSuccess: Boolean, message: String) {
                    binding.mbMakePurchase.isEnabled = isSuccess
                    Log.d("TAG", "onConnectionResult: $isSuccess - $message")
                }

                override fun onOldPurchaseResult(isPurchased: Boolean) {
                    // Update your shared-preferences here!
                    Log.d("TAG", "onOldPurchaseResult: $isPurchased")
                }
            })
        }
    }

    private fun onPurchaseClick() {
        billingManager.makeInAppPurchase(object : OnPurchaseListener {
            override fun onPurchaseResult(isPurchaseSuccess: Boolean, message: String) {
                Toast.makeText(this@MainActivity, message, Toast.LENGTH_SHORT).show()
                binding.mbMakePurchase.isEnabled = !isPurchaseSuccess
            }
        })

        billingManager.makeSubPurchase(SubscriptionTags.basicMonthly, object : OnPurchaseListener {
            override fun onPurchaseResult(isPurchaseSuccess: Boolean, message: String) {
                Toast.makeText(this@MainActivity, message, Toast.LENGTH_SHORT).show()
                binding.mbMakePurchase.isEnabled = !isPurchaseSuccess
            }
        })
    }
}