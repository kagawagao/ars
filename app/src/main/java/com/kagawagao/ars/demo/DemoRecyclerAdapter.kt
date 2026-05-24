package com.kagawagao.ars.demo

import android.os.Build
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.annotation.RequiresApi
import androidx.recyclerview.widget.RecyclerView

/**
 * Demo RecyclerView adapter demonstrating automatic skin support.
 *
 * Since [ArsActivity] wraps the Context, `LayoutInflater.from(parent.context)`
 * automatically returns skin-aware Views. No special adapter code needed.
 */
@RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
class DemoRecyclerAdapter(private val itemCount: Int) :
    RecyclerView.Adapter<DemoRecyclerAdapter.VH>() {

    class VH(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val title: TextView = itemView.findViewById(R.id.tvItemTitle)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_recycler_demo, parent, false)
        return VH(view)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.title.text = "Item #${position + 1}"
    }

    override fun getItemCount(): Int = itemCount
}
