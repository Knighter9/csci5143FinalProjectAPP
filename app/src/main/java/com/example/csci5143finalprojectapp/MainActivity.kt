package com.example.csci5143finalprojectapp
import androidx.core.content.ContextCompat
import android.bluetooth.BluetoothAdapter
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import android.Manifest
import com.example.csci5143finalprojectapp.LightControlActivity
import android.view.View
import java.util.UUID
import android.annotation.SuppressLint
import android.util.Log
import android.app.Activity
import android.app.AlertDialog
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import com.example.csci5143finalprojectapp.bluetoothConnectionMng
import android.bluetooth.BluetoothGattCallback
import android.os.Handler
import android.os.Looper
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.annotation.UiThread
import androidx.core.app.ActivityCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.SimpleItemAnimator
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothProfile
import android.bluetooth.BluetoothGattCharacteristic
import kotlin.math.log


private const val ENABLE_BLUETOOTH_REQUEST_CODE = 7
private const val RUNTIME_PERMISSION_REQUEST_CODE = 17
@SuppressLint("MissingPermission") // App's role to ensure
class MainActivity : AppCompatActivity(), BluetoothConnectionCallback{
    // the punch through guide was used to develp a lot of the bluetooth logic. A lot of functions were modeled or used from
    // them. https://punchthrough.com/android-ble-guide/ specifically scanning and blueooth permisions based stuff seen below.
    // private variables
    // used for detecting whether or not we are scanning for ble devices
    private var scanningBLEDevices = false
    // setting basic scan settings for the ble.
    private val scanSettings = ScanSettings.Builder()
        .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
        .build()
    // will be used later to scan for ble devices
    private val scannerForFindingBLEDevices by lazy{
        bluetoothAdapter.bluetoothLeScanner
    }
    // i
    private val bluetoothAdapter: BluetoothAdapter by lazy {
        val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothManager.adapter
    }
    private val scanCallback = object : ScanCallback() {
        // overiding method that is called when a new BLE device is found during scanning
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            // check to see if the found device is the one we are looking for by filtering by name of device.
            if (result.device.name == "ZettySeason_1FD3"){// this is the name for out ble deveice
                val indexQuery = scanResults.indexOfFirst { it.device.address == result.device.address }
                if (indexQuery != -1) { // we already have this result
                    scanResults[indexQuery] = result
                    scanResultAdapter.notifyItemChanged(indexQuery)
                } else {
                    with(result.device) {
                        Log.i(
                            "Inside Scan func",
                             "new ble device! Name: ${name ?: "Unnamed"}"
                        )
                    }
                    scanResults.add(result)
                    scanResultAdapter.notifyItemInserted(scanResults.size - 1)
                }
            }
        }

        override fun onScanFailed(errorCode: Int) {
            Log.e("scan func", "sorry but scan failed: code $errorCode")
        }
    }
    private val scanResults = mutableListOf<ScanResult>()
    private val scanResultAdapter: ScanningClasses by lazy {
        ScanningClasses(scanResults){result ->
            // check to see if we we are currenling scaning for devices, if so then stop scanning and attempt conenction
            if(scanningBLEDevices){
                stopBleScan()
            }
            with(result.device) {
                Log.w("about to connect", "Connecting to $address")
                bluetoothConnectionMng.connectToBLEDevice(this@MainActivity,address,this@MainActivity)
            }
        }
    }

    // this will be called when the ble conneciton has been established, we will launch the
    // light control activity after which will give a UI for the user to update system settings.
    override fun onConnectionSuccess() {
        runOnUiThread {
            Log.i("mainActrivity","about to start the new activity screen")
            startActivity(Intent(this@MainActivity, LightControlActivity::class.java))
        }
    }
    // handles the case for where the connection was not sucessful.
    override fun onConnectionFailed() {
        runOnUiThread {
            Log.i("MainActivity", "sorry the connection couldn't be established")
            Toast.makeText(this@MainActivity, "Sorry but the connection didn't work", Toast.LENGTH_SHORT).show()
        }
    }
    // simple func to prompt the user to enable bluetooth.
    override fun onResume() {
        super.onResume()
        if (!bluetoothAdapter.isEnabled) {
            askUserToEnableBluetooth()
        }
    }

    // function to enable bluetooth
    private fun askUserToEnableBluetooth() {
        if (!bluetoothAdapter.isEnabled) {
            val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            startActivityForResult(enableBtIntent, ENABLE_BLUETOOTH_REQUEST_CODE)
        }
    }
    // override func to see if blueototh was enabled and prompt if failure
    override fun onActivityResult(request: Int, result: Int, data: Intent?) {
        super.onActivityResult(request, result, data)
        when (request) {
            ENABLE_BLUETOOTH_REQUEST_CODE -> {
                if (result != Activity.RESULT_OK) {
                    askUserToEnableBluetooth()
                }
            }
        }
    }
    // context extensions functions
    fun Context.isPermissionGranted(permissionType: String): Boolean {
        return ContextCompat.checkSelfPermission(this, permissionType) ==
                PackageManager.PERMISSION_GRANTED
    }
    @RequiresApi(Build.VERSION_CODES.S)
    fun Context.hasBluetoothRequiredPerms(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            isPermissionGranted(Manifest.permission.BLUETOOTH_SCAN) &&
                    isPermissionGranted(Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            isPermissionGranted(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }


    // start ble scan,
    // will check to see fi required permisions are handled
    // if not then will prompt
    // else will clear the list of scan results and start the scan
    private fun startBleScan() {
        if (!hasBluetoothRequiredPerms()) {
            requestRelevantRuntimePermissions()
        } else {
            scanResults.clear()
            scanResultAdapter.notifyDataSetChanged()
            scannerForFindingBLEDevices.startScan(null, scanSettings, scanCallback)
            scanningBLEDevices = true
        }
    }
    // simple func will stop the scan and update variable to idnicate its stopped
    private fun stopBleScan() {
        scannerForFindingBLEDevices.stopScan(scanCallback)
        scanningBLEDevices = false
    }
    // bluetooth based permisions
    private fun Activity.requestRelevantRuntimePermissions() {
        if (hasBluetoothRequiredPerms()) { return }
        when {
            Build.VERSION.SDK_INT < Build.VERSION_CODES.S -> {
                requestLocationPermission()
            }
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
                requestBluetoothPermissions()
            }
        }
    }

    private fun requestLocationPermission() {
        runOnUiThread {
            AlertDialog.Builder(this)
                .setTitle("Location please")
                .setMessage("Bla bal bla ")
                .setCancelable(false)
                .setPositiveButton(android.R.string.ok) { dialog, _ ->
                    ActivityCompat.requestPermissions(
                        this,
                        arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                        RUNTIME_PERMISSION_REQUEST_CODE
                    )
                    dialog.dismiss()
                }
                .show()
        }
    }

    private fun requestBluetoothPermissions() {
        runOnUiThread {
            AlertDialog.Builder(this)
                .setTitle("Bluetooth permissions required")
                .setMessage("bla bal bla ")
                .setCancelable(false)
                .setPositiveButton(android.R.string.ok) { dialog, _ ->
                    ActivityCompat.requestPermissions(
                        this,
                        arrayOf(
                            Manifest.permission.BLUETOOTH_SCAN,
                            Manifest.permission.BLUETOOTH_CONNECT
                        ),
                        RUNTIME_PERMISSION_REQUEST_CODE
                    )
                    dialog.dismiss()
                }.show()
        }
    }
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            RUNTIME_PERMISSION_REQUEST_CODE -> {
                val containsPermanentDenial = permissions.zip(grantResults.toTypedArray()).any {
                    it.second == PackageManager.PERMISSION_DENIED &&
                            !ActivityCompat.shouldShowRequestPermissionRationale(this, it.first)
                }
                val containsDenial = grantResults.any { it == PackageManager.PERMISSION_DENIED }
                val allGranted = grantResults.all { it == PackageManager.PERMISSION_GRANTED }
                when {
                    containsPermanentDenial -> {
                        // nothing to do here
                    }
                    containsDenial -> {
                        requestRelevantRuntimePermissions()
                    }
                    allGranted && hasBluetoothRequiredPerms() -> {
                        startBleScan()
                    }
                    else -> {
                        // restart
                        recreate()
                    }
                }
            }
        }
    }

    // on crate method handles the basic set up for the page that the first user sees
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
        val containerView: RecyclerView = findViewById(R.id.dummyContainer)
        containerView.layoutManager = LinearLayoutManager(this)
        containerView.adapter = scanResultAdapter

        // get the submit button
        val button1 = findViewById<Button>(R.id.little_scan_button)
        if(scanningBLEDevices){
            button1.text = "Stop Scan"
        }
        else{
            button1.text = "Start Scan"
        }
        // setting up click handler for the submit button
        button1.setOnClickListener {
            if (scanningBLEDevices) {// stop ble scan
                stopBleScan()
                button1.text = "Start Scan"
            } else { // start ble scann
                startBleScan()
                button1.text = "Stop Scan"
            }
        }
    }
/*package com.example.csci5143finalprojectapp
import androidx.core.content.ContextCompat
import android.bluetooth.BluetoothAdapter
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import android.Manifest
import com.example.csci5143finalprojectapp.LightControlActivity
import android.view.View
import java.util.UUID
import android.annotation.SuppressLint
import android.util.Log
import android.app.Activity
import android.app.AlertDialog
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import com.example.csci5143finalprojectapp.bluetoothConnectionMngz%
import android.bluetooth.BluetoothGattCallback
import android.os.Handler
import android.os.Looper
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.annotation.UiThread
import androidx.core.app.ActivityCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.SimpleItemAnimator
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothProfile
import android.bluetooth.BluetoothGattCharacteristic
import kotlin.math.log


private const val ENABLE_BLUETOOTH_REQUEST_CODE = 7
private const val RUNTIME_PERMISSION_REQUEST_CODE = 17
@SuppressLint("MissingPermission") // App's role to ensure
class MainActivity : AppCompatActivity(), BluetoothConnectionCallback{

    // hanlde blueooth read callback
    //private var bluetoothGatt: BluetoothGatt? = null
    override fun onConnectionSuccess() {
        runOnUiThread {
            Log.i("mainActrivity","about to start the new activity screen")
            startActivity(Intent(this@MainActivity, LightControlActivity::class.java))
        }
    }
    override fun onConnectionFailed() {
        runOnUiThread {
            Toast.makeText(this@MainActivity, "Connection failed", Toast.LENGTH_SHORT).show()
        }
    }


    private val scanResults = mutableListOf<ScanResult>()
    private val scanResultAdapter: ScanningClasses by lazy {
        ScanningClasses(scanResults){result ->
            // will implement
            if(scanningForBLE){
                stopBleScan()
            }
            with(result.device) {
                Log.w("ScanResultAdapter", "Connecting to $address")
                bluetoothConnectionMng.connectToBLEDevice(this@MainActivity,address,this@MainActivity)
                //startActivity(Intent(this@MainActivity,LightControlActivity::class.java))
                //connectGatt(this@MainActivity, false, gattCallback)
            }
        }
    }
    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            if (result.device.name == "ZettySeason_1FD3"){
                val indexQuery = scanResults.indexOfFirst { it.device.address == result.device.address }
                if (indexQuery != -1) { // A scan result already exists with the same address
                    scanResults[indexQuery] = result
                    scanResultAdapter.notifyItemChanged(indexQuery)
                } else {
                    with(result.device) {
                        Log.i(
                            "ScanCallback",
                            "Found BLE device! Name: ${name ?: "Unnamed"}, address: $address"
                        )
                    }
                    scanResults.add(result)
                    scanResultAdapter.notifyItemInserted(scanResults.size - 1)
                }
            }
        }

        override fun onScanFailed(errorCode: Int) {
            Log.e("ScanCallback", "onScanFailed: code $errorCode")
        }
    }
    private var scanningForBLE = false
    private val scanSettings = ScanSettings.Builder()
        .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
        .build()
    private val bleScanner by lazy{
        bluetoothAdapter.bluetoothLeScanner
    }
    // important variables
    private val bluetoothAdapter: BluetoothAdapter by lazy {
        val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothManager.adapter
    }

    override fun onResume() {
        super.onResume()
        if (!bluetoothAdapter.isEnabled) {
            promptEnableBluetooth()
        }
    }

    private fun promptEnableBluetooth() {
        if (!bluetoothAdapter.isEnabled) {
            val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            startActivityForResult(enableBtIntent, ENABLE_BLUETOOTH_REQUEST_CODE)
        }
    }
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        when (requestCode) {
            ENABLE_BLUETOOTH_REQUEST_CODE -> {
                if (resultCode != Activity.RESULT_OK) {
                    promptEnableBluetooth()
                }
            }
        }
    }


    // context extensions functions
    fun Context.hasPermission(permissionType: String): Boolean {
        return ContextCompat.checkSelfPermission(this, permissionType) ==
                PackageManager.PERMISSION_GRANTED
    }
    @RequiresApi(Build.VERSION_CODES.S)
    fun Context.hasRequiredRuntimePermissions(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            hasPermission(Manifest.permission.BLUETOOTH_SCAN) &&
                    hasPermission(Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            hasPermission(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }


    // more code
    private fun startBleScan() {
        if (!hasRequiredRuntimePermissions()) {
            requestRelevantRuntimePermissions()
        } else {
            scanResults.clear()
            scanResultAdapter.notifyDataSetChanged()
            bleScanner.startScan(null, scanSettings, scanCallback)
            scanningForBLE = true
        }
    }
    private fun stopBleScan() {
        bleScanner.stopScan(scanCallback)
        scanningForBLE = false
    }

    private fun Activity.requestRelevantRuntimePermissions() {
        if (hasRequiredRuntimePermissions()) { return }
        when {
            Build.VERSION.SDK_INT < Build.VERSION_CODES.S -> {
                requestLocationPermission()
            }
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
                requestBluetoothPermissions()
            }
        }
    }

    private fun requestLocationPermission() {
        runOnUiThread {
            AlertDialog.Builder(this)
                .setTitle("Location permission required")
                .setMessage("Starting from Android M (6.0), the system requires apps to be granted " +
                        "location access in order to scan for BLE devices.")
                .setCancelable(false)
                .setPositiveButton(android.R.string.ok) { dialog, _ ->
                    ActivityCompat.requestPermissions(
                        this,
                        arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                        RUNTIME_PERMISSION_REQUEST_CODE
                    )
                    dialog.dismiss()
                }
                .show()
        }
    }

    private fun requestBluetoothPermissions() {
        runOnUiThread {
            AlertDialog.Builder(this)
                .setTitle("Bluetooth permissions required")
                .setMessage("Starting from Android 12, the system requires apps to be granted " +
                        "Bluetooth access in order to scan for and connect to BLE devices.")
                .setCancelable(false)
                .setPositiveButton(android.R.string.ok) { dialog, _ ->
                    ActivityCompat.requestPermissions(
                        this,
                        arrayOf(
                            Manifest.permission.BLUETOOTH_SCAN,
                            Manifest.permission.BLUETOOTH_CONNECT
                        ),
                        RUNTIME_PERMISSION_REQUEST_CODE
                    )
                    dialog.dismiss()
                }.show()
        }
    }
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            RUNTIME_PERMISSION_REQUEST_CODE -> {
                val containsPermanentDenial = permissions.zip(grantResults.toTypedArray()).any {
                    it.second == PackageManager.PERMISSION_DENIED &&
                            !ActivityCompat.shouldShowRequestPermissionRationale(this, it.first)
                }
                val containsDenial = grantResults.any { it == PackageManager.PERMISSION_DENIED }
                val allGranted = grantResults.all { it == PackageManager.PERMISSION_GRANTED }
                when {
                    containsPermanentDenial -> {
                        // TODO: Handle permanent denial (e.g., show AlertDialog with justification)
                        // Note: The user will need to navigate to App Settings and manually grant
                        // permissions that were permanently denied
                    }
                    containsDenial -> {
                        requestRelevantRuntimePermissions()
                    }
                    allGranted && hasRequiredRuntimePermissions() -> {
                        startBleScan()
                    }
                    else -> {
                        // Unexpected scenario encountered when handling permissions
                        recreate()
                    }
                }
            }
        }
    }

    // on crate method handles the basic set up for the page that the first user sees
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
        val recyclerView: RecyclerView = findViewById(R.id.myRecyclerView)
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = scanResultAdapter

        val button1 = findViewById<Button>(R.id.submit_scan_button)
        button1.text = if (scanningForBLE) "Stop Scan" else "Start Scan"


        button1.setOnClickListener {
            if (scanningForBLE) {
                stopBleScan()
            } else {
                startBleScan()
            }
            button1.text = if (scanningForBLE) "Stop Scan" else "Start Scan"
        }
    }
    */

/*
// funcs for writing to a characteristic
fun writeCharacteristic(characteristic: BluetoothGattCharacteristic, payload: ByteArray) {
    val writeType = when {
        characteristic.isWritable() -> BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
        characteristic.isWritableWithoutResponse() -> {
            BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
        }
        else -> error("Characteristic ${characteristic.uuid} cannot be written to")
    }

    bluetoothGatt?.let { gatt ->
        characteristic.writeType = writeType
        characteristic.value = payload
        gatt.writeCharacteristic(characteristic)
    } ?: error("Not connected to a BLE device!")
}
// func for writing light level
private fun writeLightLevel() {
    val lightLevelServiceUuid = UUID.fromString("0000183f-0000-1000-8000-00805f9b34fb")
    val lightLevelCharacteristicUuid = UUID.fromString("00002a80-0000-1000-8000-00805f9b34fb")
    val lightLevelChar = bluetoothGatt?.getService(lightLevelServiceUuid)?.getCharacteristic(lightLevelCharacteristicUuid)

    if (lightLevelChar?.isWritable() == true || lightLevelChar?.isWritableWithoutResponse() == true) {
        val payload = byteArrayOf(0x05.toByte())
        writeCharacteristic(lightLevelChar, payload)
    } else {
        Log.e("writeLightLevel", "Light Level Characteristic is not writable")
    }
}
// func for reading light level
private fun readLightLevel(){
    val lightLevelServiceUuid = UUID.fromString("0000183f-0000-1000-8000-00805f9b34fb")
    val lightLevelCharacteristicUuid = UUID.fromString("00002a80-0000-1000-8000-00805f9b34fb")
    val lightLevel = bluetoothGatt?.getService(lightLevelServiceUuid)?.getCharacteristic(lightLevelCharacteristicUuid)
    if(lightLevel?.isReadable()==true){
        bluetoothGatt?.readCharacteristic(lightLevel)
    }
}
// functions for checking property characteristics
fun BluetoothGattCharacteristic.isReadable(): Boolean =
    containsProperty(BluetoothGattCharacteristic.PROPERTY_READ)

fun BluetoothGattCharacteristic.isWritable(): Boolean =
    containsProperty(BluetoothGattCharacteristic.PROPERTY_WRITE)

fun BluetoothGattCharacteristic.isWritableWithoutResponse(): Boolean =
    containsProperty(BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE)

fun BluetoothGattCharacteristic.containsProperty(property: Int): Boolean {
    return properties and property != 0
}
fun ByteArray.toHexString(): String =
    joinToString(separator = " ", prefix = "0x") { String.format("%02X", it) }
// hanlde blueooth read callback
private var bluetoothGatt: BluetoothGatt? = null
private val gattCallback = object : BluetoothGattCallback() {
    override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
        val deviceAddress = gatt.device.address

        if (status == BluetoothGatt.GATT_SUCCESS) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                Log.w("BluetoothGattCallback", "Successfully connected to $deviceAddress")
                bluetoothGatt = gatt
                Handler(Looper.getMainLooper()).post {
                    bluetoothGatt?.discoverServices()
                }
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                Log.w("BluetoothGattCallback", "Successfully disconnected from $deviceAddress")
                gatt.close()
            }
        } else {
            Log.w(
                "BluetoothGattCallback",
                "Error $status encountered for $deviceAddress! Disconnecting..."
            )
            gatt.close()
        }
    }
    override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
        with(gatt) {
            Log.w(
                "BluetoothGattCallback",
                "Discovered ${services.size} services for ${device.address}"
            )
            printGattTable()
        }
    }
    override fun onCharacteristicRead(
        gatt: BluetoothGatt,
        characteristic: BluetoothGattCharacteristic,
        status: Int
    ) {
        with(characteristic) {
            when (status) {
                BluetoothGatt.GATT_SUCCESS -> {
                    Log.i("BluetoothGattCallback", "Read characteristic $uuid:\n${value.toHexString()}")
                }
                BluetoothGatt.GATT_READ_NOT_PERMITTED -> {
                    Log.e("BluetoothGattCallback", "Read not permitted for $uuid!")
                }
                else -> {
                    Log.e("BluetoothGattCallback", "Characteristic read failed for $uuid, error: $status")
                }
            }
        }
    }
    override fun onCharacteristicWrite(
        gatt: BluetoothGatt,
        characteristic: BluetoothGattCharacteristic,
        status: Int
    ) {
        with(characteristic) {
            when (status) {
                BluetoothGatt.GATT_SUCCESS -> {
                    Log.i("BluetoothGattCallback", "Wrote to characteristic $uuid | value: ${value.toHexString()}")
                }
                BluetoothGatt.GATT_INVALID_ATTRIBUTE_LENGTH -> {
                    Log.e("BluetoothGattCallback", "Write exceeded connection ATT MTU!")
                }
                BluetoothGatt.GATT_WRITE_NOT_PERMITTED -> {
                    Log.e("BluetoothGattCallback", "Write not permitted for $uuid!")
                }
                else -> {
                    Log.e("BluetoothGattCallback", "Characteristic write failed for $uuid, error: $status")
                }
            }
        }
    }
}
private val scanResults = mutableListOf<ScanResult>()
private val scanResultAdapter: ScanningClasses by lazy {
    ScanningClasses(scanResults){result ->
        // will implement
        if(scanningForBLE){
            stopBleScan()
        }
        with(result.device) {
            Log.w("ScanResultAdapter", "Connecting to $address")
            connectGatt(this@MainActivity, false, gattCallback)
        }
    }
}
private val scanCallback = object : ScanCallback() {
    override fun onScanResult(callbackType: Int, result: ScanResult) {
        val indexQuery = scanResults.indexOfFirst { it.device.address == result.device.address }
        if (indexQuery != -1) { // A scan result already exists with the same address
            scanResults[indexQuery] = result
            scanResultAdapter.notifyItemChanged(indexQuery)
        } else {
            with(result.device) {
                Log.i("ScanCallback", "Found BLE device! Name: ${name ?: "Unnamed"}, address: $address")
            }
            scanResults.add(result)
            scanResultAdapter.notifyItemInserted(scanResults.size - 1)
        }
    }

    override fun onScanFailed(errorCode: Int) {
        Log.e("ScanCallback", "onScanFailed: code $errorCode")
    }
}
private fun BluetoothGatt.printGattTable() {
    if (services.isEmpty()) {
        Log.i("printGattTable", "No service and characteristic available, call discoverServices() first?")
        return
    }
    services.forEach { service ->
        val characteristicsTable = service.characteristics.joinToString(
            separator = "\n|--",
            prefix = "|--"
        ) { it.uuid.toString() }
        Log.i("printGattTable", "\nService ${service.uuid}\nCharacteristics:\n$characteristicsTable"
        )
    }
}
private var scanningForBLE = false
private val scanSettings = ScanSettings.Builder()
    .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
    .build()
private val bleScanner by lazy{
    bluetoothAdapter.bluetoothLeScanner
}
// important variables
private val bluetoothAdapter: BluetoothAdapter by lazy {
    val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    bluetoothManager.adapter
}

override fun onResume() {
    super.onResume()
    if (!bluetoothAdapter.isEnabled) {
        promptEnableBluetooth()
    }
}

private fun promptEnableBluetooth() {
    if (!bluetoothAdapter.isEnabled) {
        val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
        startActivityForResult(enableBtIntent, ENABLE_BLUETOOTH_REQUEST_CODE)
    }
}
override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
    super.onActivityResult(requestCode, resultCode, data)
    when (requestCode) {
        ENABLE_BLUETOOTH_REQUEST_CODE -> {
            if (resultCode != Activity.RESULT_OK) {
                promptEnableBluetooth()
            }
        }
    }
}


// context extensions functions
fun Context.hasPermission(permissionType: String): Boolean {
    return ContextCompat.checkSelfPermission(this, permissionType) ==
            PackageManager.PERMISSION_GRANTED
}
@RequiresApi(Build.VERSION_CODES.S)
fun Context.hasRequiredRuntimePermissions(): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        hasPermission(Manifest.permission.BLUETOOTH_SCAN) &&
                hasPermission(Manifest.permission.BLUETOOTH_CONNECT)
    } else {
        hasPermission(Manifest.permission.ACCESS_FINE_LOCATION)
    }
}


// more code
private fun startBleScan() {
    if (!hasRequiredRuntimePermissions()) {
        requestRelevantRuntimePermissions()
    } else {
        scanResults.clear()
        scanResultAdapter.notifyDataSetChanged()
        bleScanner.startScan(null, scanSettings, scanCallback)
        scanningForBLE = true
    }
}
private fun stopBleScan() {
    bleScanner.stopScan(scanCallback)
    scanningForBLE = false
}

private fun Activity.requestRelevantRuntimePermissions() {
    if (hasRequiredRuntimePermissions()) { return }
    when {
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S -> {
            requestLocationPermission()
        }
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            requestBluetoothPermissions()
        }
    }
}

private fun requestLocationPermission() {
    runOnUiThread {
        AlertDialog.Builder(this)
            .setTitle("Location permission required")
            .setMessage("Starting from Android M (6.0), the system requires apps to be granted " +
                    "location access in order to scan for BLE devices.")
            .setCancelable(false)
            .setPositiveButton(android.R.string.ok) { dialog, _ ->
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                    RUNTIME_PERMISSION_REQUEST_CODE
                )
                dialog.dismiss()
            }
            .show()
    }
}

private fun requestBluetoothPermissions() {
    runOnUiThread {
        AlertDialog.Builder(this)
            .setTitle("Bluetooth permissions required")
            .setMessage("Starting from Android 12, the system requires apps to be granted " +
                    "Bluetooth access in order to scan for and connect to BLE devices.")
            .setCancelable(false)
            .setPositiveButton(android.R.string.ok) { dialog, _ ->
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(
                        Manifest.permission.BLUETOOTH_SCAN,
                        Manifest.permission.BLUETOOTH_CONNECT
                    ),
                    RUNTIME_PERMISSION_REQUEST_CODE
                )
                dialog.dismiss()
            }.show()
    }
}
override fun onRequestPermissionsResult(
    requestCode: Int,
    permissions: Array<out String>,
    grantResults: IntArray
) {
    super.onRequestPermissionsResult(requestCode, permissions, grantResults)
    when (requestCode) {
        RUNTIME_PERMISSION_REQUEST_CODE -> {
            val containsPermanentDenial = permissions.zip(grantResults.toTypedArray()).any {
                it.second == PackageManager.PERMISSION_DENIED &&
                        !ActivityCompat.shouldShowRequestPermissionRationale(this, it.first)
            }
            val containsDenial = grantResults.any { it == PackageManager.PERMISSION_DENIED }
            val allGranted = grantResults.all { it == PackageManager.PERMISSION_GRANTED }
            when {
                containsPermanentDenial -> {
                    // TODO: Handle permanent denial (e.g., show AlertDialog with justification)
                    // Note: The user will need to navigate to App Settings and manually grant
                    // permissions that were permanently denied
                }
                containsDenial -> {
                    requestRelevantRuntimePermissions()
                }
                allGranted && hasRequiredRuntimePermissions() -> {
                    startBleScan()
                }
                else -> {
                    // Unexpected scenario encountered when handling permissions
                    recreate()
                }
            }
        }
    }
}

// on crate method handles the basic set up for the page that the first user sees
override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    setContentView(R.layout.activity_main)
    ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
        val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
        v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
        insets
    }
    val recyclerView: RecyclerView = findViewById(R.id.myRecyclerView)
    recyclerView.layoutManager = LinearLayoutManager(this)
    recyclerView.adapter = scanResultAdapter

    val button1 = findViewById<Button>(R.id.submit_scan_button)
    button1.text = if (scanningForBLE) "Stop Scan" else "Start Scan"


    button1.setOnClickListener {
        if (scanningForBLE) {
            stopBleScan()
        } else {
            startBleScan()
        }
        button1.text = if (scanningForBLE) "Stop Scan" else "Start Scan"
    }
    val button2 = findViewById<Button>(R.id.readlightLevelButton)

    button2.setOnClickListener(){
        readLightLevel()
    }

    val button3 = findViewById<Button>(R.id.writeLightLevelButton)
    button3.setOnClickListener(){
        writeLightLevel()
    }

}
 */
// select the basic buttion

}