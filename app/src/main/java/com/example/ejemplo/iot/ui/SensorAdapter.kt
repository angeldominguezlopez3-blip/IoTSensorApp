package com.ejemplo.iot.ui

import android.graphics.Color
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
            LayoutInflater.from(parent.context), parent, false
        )
        return SensorViewHolder(binding)
    }

    override fun onBindViewHolder(holder: SensorViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount() = items.size

    class SensorViewHolder(private val binding: ItemSensorBinding) :
        RecyclerView.ViewHolder(binding.root) {

        private val dateFormat = SimpleDateFormat("HH:mm:ss dd/MM", Locale.getDefault())

        fun bind(reading: SensorReading) {
            binding.textTime.text = dateFormat.format(reading.timestamp)
            binding.textPresence.text = if (reading.presencia) "👤 Persona" else "👤 Vacío"
            binding.textTemperature.text = "${String.format("%.1f", reading.temperatura)}°C"
            binding.textSound.text = "🔊 ${reading.sonido}%"

            // Nivel de estrés basado en temperatura y sonido
            val nivel = calcularNivel(reading.temperatura, reading.sonido)
            binding.textStress.text = nivel.label
            binding.textStressLabel.text = nivel.emoji
            binding.root.setCardBackgroundColor(nivel.cardColor)
            binding.textTemperature.setTextColor(nivel.tempColor)
        }

        private data class Nivel(
            val label: String,
            val emoji: String,
            val cardColor: Int,
            val tempColor: Int
        )

        private fun calcularNivel(temp: Float, sonido: Int): Nivel {
            return when {
                temp >= 30 || sonido >= 75 -> Nivel(
                    label = "Crítico",
                    emoji = "🔴",
                    cardColor = Color.parseColor("#FFEBEE"),
                    tempColor = Color.parseColor("#C62828")
                )
                temp >= 26 || sonido >= 55 -> Nivel(
                    label = "Medio",
                    emoji = "🟡",
                    cardColor = Color.parseColor("#FFF8E1"),
                    tempColor = Color.parseColor("#F57F17")
                )
                else -> Nivel(
                    label = "Bajo",
                    emoji = "🟢",
                    cardColor = Color.parseColor("#F1F8E9"),
                    tempColor = Color.parseColor("#2E7D32")
                )
            }
        }
    }
}