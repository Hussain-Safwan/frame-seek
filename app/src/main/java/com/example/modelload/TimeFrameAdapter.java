package com.example.modelload;

import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.cardview.widget.CardView;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.exoplayer2.ExoPlayer;

import java.util.ArrayList;
import java.util.List;

public class TimeFrameAdapter extends RecyclerView.Adapter<TimeFrameAdapter.ViewHolder> {

    private List<TimeFrame> timeFrames = new ArrayList<>();
    private final ExoPlayer player;

    public TimeFrameAdapter(List<TimeFrame> timeFrames, ExoPlayer player) {
        this.timeFrames = timeFrames;
        this.player = player;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_card, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        TimeFrame frame = timeFrames.get(position);
        Log.d("debug", String.valueOf(frame.queryImageUri));
        if (frame.queryImageUri != null)
        {
            holder.frameImage.setVisibility(View.VISIBLE);
            holder.labelText.setVisibility(View.GONE);
            holder.frameImage.setImageURI(frame.queryImageUri);

        }
        else
        {
            holder.frameImage.setVisibility(View.GONE);
            holder.labelText.setVisibility(View.VISIBLE);
            holder.labelText.setText(frame.query);
        }
        holder.timeText.setText(String.format("%02d:%02d", (0), (frame.timeMillis)));
        holder.card.setOnClickListener(v -> player.seekTo(frame.timeMillis * 1000));
    }

    @Override
    public int getItemCount() {
        return timeFrames.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        TextView labelText, timeText;
        ImageView frameImage;
        CardView card;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            labelText = itemView.findViewById(R.id.label_text);
            timeText = itemView.findViewById(R.id.time_frame_text);
            frameImage = itemView.findViewById(R.id.frameImage);
            card = itemView.findViewById(R.id.timeCard);
        }
    }
}
