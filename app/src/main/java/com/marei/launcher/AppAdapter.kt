package com.marei.launcher

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class AppAdapter(
    private val mode: Mode,
    private val onClick: (View, AppEntry) -> Unit,
    private val onLongClick: (View, AppEntry) -> Unit,
) : RecyclerView.Adapter<AppAdapter.Holder>() {

    enum class Mode { TILE, ROW, DOCK }

    var items: List<AppEntry> = emptyList()
        private set

    @SuppressLint("NotifyDataSetChanged")
    fun submit(list: List<AppEntry>) {
        items = list
        notifyDataSetChanged()
    }

    class Holder(v: View) : RecyclerView.ViewHolder(v) {
        val icon: ImageView = v.findViewById(R.id.icon)
        val label: TextView? = v.findViewById(R.id.label)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val layout = when (mode) {
            Mode.TILE -> R.layout.item_tile
            Mode.ROW -> R.layout.item_row
            Mode.DOCK -> R.layout.item_dock
        }
        return Holder(LayoutInflater.from(parent.context).inflate(layout, parent, false))
    }

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val app = items[position]
        holder.icon.setImageBitmap(app.icon)
        holder.label?.text = app.label
        holder.itemView.contentDescription = app.label
        holder.itemView.setOnClickListener { onClick(it, app) }
        holder.itemView.setOnLongClickListener { onLongClick(it, app); true }
    }

    override fun getItemCount() = items.size
}
