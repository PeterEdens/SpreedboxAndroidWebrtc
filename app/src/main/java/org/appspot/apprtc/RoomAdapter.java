package org.appspot.apprtc;

import android.content.Context;
import android.graphics.Typeface;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import java.util.ArrayList;

public class RoomAdapter extends ArrayAdapter<String> {
    Context mContext;
    String mCurrentRoom = "";

    public RoomAdapter(Context context, int textViewId, int layout_id, ArrayList<String> items){
        super(context, textViewId, layout_id, items);
        mContext = context;
    }

    public void setCurrentRoom(String roomName) {
        mCurrentRoom = roomName;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent){
        if (convertView == null) {
            LayoutInflater inflater = (LayoutInflater) this.getContext().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            convertView = inflater.inflate(R.layout.rooms_list,parent,false);
        }
        // Get the data item for this position
        String name = (String)getItem(position);

        // Lookup view for data population
        TextView text1 = (TextView) convertView.findViewById(R.id.text_id);
        ImageView joinedStatus = (ImageView) convertView.findViewById(R.id.joined_status);
        TextView messageStatus = (TextView) convertView.findViewById(R.id.message_status);

        if ((mMessages.containsKey(name) && mMessages.get(name) != 0) ||
                mFiles.containsKey(name) && mFiles.get(name) != 0) {
            messageStatus.setText(String.valueOf(mMessages.get(name)));
            messageStatus.setVisibility(View.VISIBLE);
        }
        else {
            messageStatus.setVisibility(View.GONE);
        }

        if (name.equals(mCurrentRoom)) {
            joinedStatus.setVisibility(View.VISIBLE);
            text1.setTypeface(null, Typeface.BOLD);
        }
        else {
            joinedStatus.setVisibility(View.GONE);
            text1.setTypeface(null, Typeface.NORMAL);
        }

        // Populate the data into the template view using the data object
        if (name.length() == 0) {
            name = mContext.getString(R.string.default_room);
        }
        text1.setText(name);

        return convertView;
    }
}