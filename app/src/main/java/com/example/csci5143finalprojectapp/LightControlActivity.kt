package com.example.csci5143finalprojectapp

import android.os.Bundle
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.csci5143finalprojectapp.bluetoothConnectionMng
import com.google.android.material.slider.Slider

// handles the logic for the display screen seen by the user after they connect
// to the ble device so basically handles logic for button presses to turn the water pump on
// and adjust the light levels.
class LightControlActivity: AppCompatActivity(), BluetoothUIUpdateListener{
    // var for indicating if plant is being water or not
    private var plantIsBeingWatered = 0
    // a basic progress bar for to display the soil moisture readings
    private lateinit var soilMoistureProgressBar: ProgressBar
    // a basic text display that will be placed above the soil moistur readings bar
    private lateinit var soilMoistureTextDislplay: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        // basic starter initialization stuff
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_light_control)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.lightLayout)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }


        // set the callback in the bluetooth connection manager
        // for handling soil moisture levels to be the lightActivityInstance
        // give us control to be notified and display soil moisture leves on the UI
        bluetoothConnectionMng.setSoilMoistureUpdateListener(this)

        // selecting the soilMoistureProgressBar
        soilMoistureProgressBar = findViewById<ProgressBar>(R.id.progressBar2)

        // selecting the soil moisture text display
        soilMoistureTextDislplay = findViewById<TextView>(R.id.SoilMoistureDisplayValue)


        // selecting the light level slider.
        val lightLevelSlider: Slider = findViewById<Slider>(R.id.light_level_slider)
        // setting up a function to react to when the user messes around with the slider
        // to change the light levels, that is adjust the duty cycle.
        lightLevelSlider.addOnChangeListener {rangeslider,value,fromUser->
            if(fromUser){ // check to see if the user interacted with the slider
                val lightLevel = value.toInt(); // get the current value of the slider (0-100) which corresponds to duty cycle
                val lightLevelBytes  = byteArrayOf(lightLevel.toByte())  // conver the value to hex for writing to ble characteristic
                bluetoothConnectionMng.writeLightLevel(lightLevelBytes) // write the hex value over ble so the server can update the duty cycle for the PWM of the lights
            }
        }
        //
        val plantButtonWater = findViewById<Button>(R.id.waterPlantButton);
        plantButtonWater.setOnClickListener(){
            if (plantIsBeingWatered == 0){// plant is not being watered so we want to turn it on
                // start watering by writing a 1 to to the server
                val byteArray = byteArrayOf(1.toByte())
                bluetoothConnectionMng.writeWaterPump(byteArray)
                plantIsBeingWatered = 1
                plantButtonWater.text="stop water"
            }
            else{// plant must be watered, so we want to turn it off thus we write a
                val byteArray = byteArrayOf(0.toByte())
                bluetoothConnectionMng.writeWaterPump(byteArray)
                plantIsBeingWatered = 0
                plantButtonWater.text="add water"

            }
        }
    }
    // implementing the interface will siimply update the progress bar
    override fun onSoilMoistureUpdate(value: Int){
        runOnUiThread {
            // Update the progress bar and show the new soilMoisture level
            soilMoistureProgressBar.progress = value

            // update the display to show integer value of the soil moisture level.
            soilMoistureTextDislplay.text = "Soil Moisture Level: $value"
        }
    }

}