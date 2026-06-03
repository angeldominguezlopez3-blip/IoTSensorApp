package com.ejemplo.iot.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.ejemplo.iot.data.models.SensorReading
import com.ejemplo.iot.databinding.ItemSensorBinding
import java.text.SimpleDateFormat
import java.util.Locale

class SensorAdapter : RecyclerView.Adapter<SensorAdapter.SensorViewHolder>() {

    private var items = listOf<SensorReading>()

    fun submitList(newItems: List<SensorReading>) {
        items = newItems
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SensorViewHolder {
        val binding = ItemSensorBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return SensorViewHolder(binding)
    }

    override fun onBindViewHolder(holder: SensorViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    class SensorViewHolder(private val binding: ItemSensorBinding) :
        RecyclerView.ViewHolder(binding.root) {

        private val dateFormat = SimpleDateFormat("HH:mm:ss dd/MM", Locale.getDefault())

        fun bind(reading: SensorReading) {
            binding.textTemperature.text = "${String.format("%.1f", reading.temperatura)}°C"
            binding.textSound.text = "🔊 ${reading.sonido}%"
            binding.textPresence.text = if (reading.presencia) "👤 Persona" else "👤 Vacío"
            binding.textTime.text = dateFormat.format(reading.timestamp)

            // Cambiar color según temperatura
            val color = when {
                reading.temperatura > 30 -> android.graphics.Color.rgb(255, 100, 100)
                reading.temperatura < 15 -> android.graphics.Color.rgb(100, 150, 255)
                else -> android.graphics.Color.rgb(100, 200, 100)
            }
            binding.textTemperature.setTextColor(color)
        }
    }
}