package com.example.csci5143finalprojectapp

import android.R
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.ProgressBar
import java.util.UUID


object bluetoothConnectionMng {
    // Note these twos guide were very helpful in develping the bluetooth logic. https://developerhelp.microchip.com/xwiki/bin/view/applications/ble/android-development-for-bm70rn4870/
    // the punch through guide was used to develp a lot of the bluetooth logic. A lot of functions were modeled or used from them. https://punchthrough.com/android-ble-guide/
    private var bluetoothGatt:BluetoothGatt? = null
    private var connectionCallback: BluetoothConnectionCallback? =null
    private var updateListener: BluetoothUIUpdateListener? = null

    // interface stuff for ui update of soil moisture leve
    fun setSoilMoistureUpdateListener(listener: BluetoothUIUpdateListener?){
        updateListener = listener
    }
    fun notifySoilMoistureLevel(value: Int){
        updateListener?.onSoilMoistureUpdate(value)
    }
    // connection logic
    fun connectToBLEDevice(context: Context, deviceAddress:String,callback: BluetoothConnectionCallback){
        this.connectionCallback = callback
        val bluetoothAdapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()
        val device = bluetoothAdapter?.getRemoteDevice(deviceAddress)
        bluetoothGatt = device?.connectGatt(context, false, gattCallback)

    }
    fun disconnectFromBLEDevice(){
        bluetoothGatt?.disconnect()
        bluetoothGatt?.close()
        bluetoothGatt = null
    }
    // main callback for bluetooth logic
    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            val deviceAddress = gatt.device.address

            if (status == BluetoothGatt.GATT_SUCCESS) {
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    Log.w("BluetoothGattCallback", "connection success")
                    bluetoothGatt = gatt
                    Handler(Looper.getMainLooper()).post {
                        bluetoothGatt?.discoverServices()
                    }
                    connectionCallback?.onConnectionSuccess()
                } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    Log.w("BluetoothGattCallback", "success disconnect")
                    gatt.close()
                    connectionCallback?.onConnectionFailed()
                }
            } else {
                Log.w(
                    "BluetoothGattCallback",
                    "Failure.. Disconnecting..."
                )
                gatt.close()
            }
        }
        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            with(gatt) {
                Log.w(
                    "BluetoothGattCallback",
                    "Discovered services for ${device.address}"

                )
                printResults()
                // enable noties for the soil moisture sensor
                enableSoilMoistureNotifications(gatt)
            }
        }
        // callback for when a characterstitic has been read
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
                        Log.e("BluetoothGattCallback", "sorry no read aloud")
                    }
                    else -> {
                        Log.e("BluetoothGattCallback", "sorry read faild")
                    }
                }
            }
        }
        // callback for when characctreistic has been written.
        override fun onCharacteristicWrite(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int
        ) {
            with(characteristic) {
                when (status) {
                    BluetoothGatt.GATT_SUCCESS -> {
                        Log.i("BluetoothGattCallback", "succes write of value ${value.toHexString()}")
                    }
                    BluetoothGatt.GATT_INVALID_ATTRIBUTE_LENGTH -> {
                        Log.e("BluetoothGattCallback", "sorry your message to long")
                    }
                    BluetoothGatt.GATT_WRITE_NOT_PERMITTED -> {
                        Log.e("BluetoothGattCallback", "sorry write not valid")
                    }
                    else -> {
                        Log.e("BluetoothGattCallback", " write failed , error: $status")
                    }
                }
            }
        }
        // callback for when characertstic changed, this will be for the soil moisture level
        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            if (characteristic.uuid == UUID.fromString("00002a31-0000-1000-8000-00805f9b34fb")) {
                Log.i("BluetoothGattCallback", "soil moisture level characteristic changed | value: ${characteristic.value.toHexString()}")
                Log.i("BluetoothGattCallback", "soil moisture level characteristic changed | value: ${characteristic.value[0].toInt()}")
                val intValue = characteristic.value[0].toInt() and 0xFF
                // write the bytes to the ble server
                notifySoilMoistureLevel(intValue)


            }
        }
    }
    // functions for writing notificaiton and enable them
    fun enableSoilMoistureNotifications(gatt: BluetoothGatt){
        val soilMoistureServiceUUID = UUID.fromString("0000181c-0000-1000-8000-00805f9b34fb")
        val soilMoistureLevelCharacteristicUUID = UUID.fromString("00002a31-0000-1000-8000-00805f9b34fb")

        val soilMoistureService = gatt.getService(soilMoistureServiceUUID)
        val soilMoistureCharacteristic = soilMoistureService?.getCharacteristic(soilMoistureLevelCharacteristicUUID)

        soilMoistureCharacteristic?.let {
            if (gatt.setCharacteristicNotification(it,true)){
                val descriptor = it.getDescriptor(UUID.fromString("00002902-0000-1000-8000-00805f9b34fb"))
                descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                gatt.writeDescriptor(descriptor)
                Log.i("notifications", "Notifications enabled for soil moisture characteristic")
            }
            else{
                Log.i("notifications", "Notifications not enabled for soil moisture characteristic")
            }
        }
    }


    // helper functions for checking property characteristics
    // checking to see if a given property has read/write permissions
    fun BluetoothGattCharacteristic.isReadable(): Boolean =
        containsProperty(BluetoothGattCharacteristic.PROPERTY_READ)

    fun BluetoothGattCharacteristic.isWritable(): Boolean =
        containsProperty(BluetoothGattCharacteristic.PROPERTY_WRITE)

    fun BluetoothGattCharacteristic.isWritableWithoutResponse(): Boolean =
        containsProperty(BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE)

    fun BluetoothGattCharacteristic.containsProperty(property: Int): Boolean {
        return properties and property != 0
    }
    // helper func for converting response from ble peripherial to hex string
    fun ByteArray.toHexString(): String =
        joinToString(separator = " ", prefix = "0x") { String.format("%02X", it) }

    // helper func for printing bluetooth information
    private fun BluetoothGatt.printResults() {
        if (services.isEmpty()) {
            Log.i("printResults", "No service and characteristic available")
            return
        }
        // for each serveice lets just print the serveice
        services.forEach { service ->
            Log.i("printGattTable", "\nService ${service.uuid}\n")
        }
    }
    // function for writing to a given characterstic
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
        } ?: error("ble not working!")
    }
    // functions for reading and writing the light level
    // func for writing to light level
    public fun writeLightLevel(contentTowrite: ByteArray) {
        val lightLevelServiceUuid = UUID.fromString("0000183f-0000-1000-8000-00805f9b34fb")
        val lightLevelCharacteristicUuid = UUID.fromString("00002a80-0000-1000-8000-00805f9b34fb")
        val lightLevelChar = bluetoothGatt?.getService(lightLevelServiceUuid)?.getCharacteristic(lightLevelCharacteristicUuid)

        if (lightLevelChar?.isWritable() == true || lightLevelChar?.isWritableWithoutResponse() == true) {
            writeCharacteristic(lightLevelChar, contentTowrite)
        } else {
            Log.e("writeLightLevel", "Light Level Characteristic is not writable")
        }
    }
    // func for writing to water pump
    public fun writeWaterPump(contentTowrite: ByteArray){
        val waterPumpServiceUuid = UUID.fromString("00001815-0000-1000-8000-00805f9b34fb")
        val waterPumpCharacteristicUuid = UUID.fromString("00002a06-0000-1000-8000-00805f9b34fb")
        val lightLevelChar = bluetoothGatt?.getService(waterPumpServiceUuid)?.getCharacteristic(waterPumpCharacteristicUuid)

        if (lightLevelChar?.isWritable() == true || lightLevelChar?.isWritableWithoutResponse() == true) {
            writeCharacteristic(lightLevelChar, contentTowrite)
        } else {
            Log.e("writeWaterPUmp", "water pump Characteristic is not writable")
        }
    }
    // func for reading light level
    // mainly used for testing purposes, functionality not found in final version of app.
    public fun readLightLevel(){
        val lightLevelServiceUuid = UUID.fromString("0000183f-0000-1000-8000-00805f9b34fb")
        val lightLevelCharacteristicUuid = UUID.fromString("00002a80-0000-1000-8000-00805f9b34fb")
        val lightLevel = bluetoothGatt?.getService(lightLevelServiceUuid)?.getCharacteristic(lightLevelCharacteristicUuid)
        if(lightLevel?.isReadable()==true){
            bluetoothGatt?.readCharacteristic(lightLevel)
        }
    }
}