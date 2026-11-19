package com.example.araspoint_map.frontend.activity

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.araspoint_map.R
import com.example.araspoint_map.backend.model.NavigationStep
import com.example.araspoint_map.backend.model.enums.TurnType
import kotlin.math.roundToInt

class DirectionStepsAdapter(
    private var steps: List<NavigationStep> = emptyList(),
    private val onStepClicked: ((NavigationStep) -> Unit)? = null
) : RecyclerView.Adapter<DirectionStepsAdapter.StepViewHolder>() {

    @SuppressLint("NotifyDataSetChanged")
    fun updateSteps(newSteps: List<NavigationStep>) {
        steps = newSteps
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): StepViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(
            R.layout.item_direction_step,
            parent,
            false
        )
        return StepViewHolder(view)
    }

    override fun onBindViewHolder(holder: StepViewHolder, position: Int) {
        val step = steps[position]
        holder.bind(step, position, steps.size)
    }

    override fun getItemCount(): Int = steps.size

    inner class StepViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val txtTurnEmoji: TextView = itemView.findViewById(R.id.txtTurnEmoji)
        private val txtStepHeader: TextView = itemView.findViewById(R.id.txtStepHeader)
        private val txtStepInstruction: TextView = itemView.findViewById(R.id.txtStepInstruction)
        private val txtStepProgress: TextView = itemView.findViewById(R.id.txtStepProgress)

        fun bind(step: NavigationStep, position: Int, total: Int) {
            val emoji = when (step.turnType) {
                TurnType.ARRIVED -> "🎯"
                TurnType.HEAD_STRAIGHT -> "⬆️"
                TurnType.TURN_SLIGHT_RIGHT -> "↗️"
                TurnType.TURN_RIGHT -> "➡️"
                TurnType.TURN_SHARP_RIGHT -> "↪️"
                TurnType.TURN_SLIGHT_LEFT -> "↖️"
                TurnType.TURN_LEFT -> "⬅️"
                TurnType.TURN_SHARP_LEFT -> "↩️"
            }

            txtTurnEmoji.text = emoji
            txtStepHeader.text = "Step ${position + 1} • ${step.distanceMeters.roundToInt()}m"
            txtStepInstruction.text = step.instruction
            txtStepProgress.text = "${position + 1} / $total"

            itemView.setOnClickListener {
                onStepClicked?.invoke(step)
            }
        }
    }
}
