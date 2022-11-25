package dev.epegasus.googleplaysbilling

import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import dev.epegasus.googleplaysbilling.databinding.ActivityMainBinding

private const val TAG = "MyTag"
class MainActivity : AppCompatActivity() {

    private val binding by lazy { ActivityMainBinding.inflate(layoutInflater) }
    private val billingManager by lazy { BillingManager(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)

        billingManager.startConnection()

        binding.tvTitle.setOnClickListener {
            Log.d(TAG, "onCreate: ${billingManager.productDetailsList[0]} ")
            billingManager.makePurchase(billingManager.productDetailsList[0])
        }

    }
}