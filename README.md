# Google-Plays-Billing



##       Android Implementation's Steps
#### Official link:   https://developer.android.com/google/play/billing/getting-ready
 
       *   Steps
 
           ->  Add Dependency
               ~   implementation "com.android.billingclient:billing-ktx:$billing_version"
 
           ->  Upload your App
 
           ->  Create and configure your products (On Console)
               ~   For each product, you need to provide a unique product ID, a title,
                   a description, and pricing information.
               ~   Subscriptions have additional required information, such as selecting 
                   whether it's an auto-renewing or prepaid renewal type for the base plan.
 
           1)  Initialize Billing Client
               ~   It is the main interface for communication between the Google Play 
                   Billing Library and the rest of your app.
           2)  Start Connection
               ~   Connection Setup Disconnected
               ~   Connection Setup Finished
           3)  Query all available products detail by giving list of product_id.
 
           // Make Purchase
           4)  Query all available products detail by giving list of product_id.
           5)  Launch billing flow (bottomSheet)
           6)  Get Response, save it in SharedPreference or else
 
