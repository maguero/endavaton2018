package com.hackaton.endava.calendar;

import android.content.Context;
import android.util.Log;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.google.ar.core.AugmentedImage;
import com.google.ar.sceneform.AnchorNode;
import com.google.ar.sceneform.Node;
import com.google.ar.sceneform.math.Vector3;
import com.google.ar.sceneform.rendering.ViewRenderable;
import com.hackaton.endava.calendar.model.MeetingData;
import com.hackaton.endava.calendar.model.MeetingRoom;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public class AugmentedImageNode extends AnchorNode {

    private static final String TAG = "AugmentedImageNode";
    private static final String START_DAY_AT = "09:00";
    private static final String END_DAY_AT = "18:00";

    // The augmented image represented by this node.
    private AugmentedImage image;

    private static Map<String, CompletableFuture<ViewRenderable>> viewMap =
            new HashMap<>();


    public AugmentedImageNode(Context context) {
        // Upon construction, start loading the models for the corners of the frame.

        for (MeetingRoom room : MeetingRoomManager.Manager.meetingRooms.values()) {
            if (viewMap.get(room.getFileName()) == null) {
                viewMap.put(room.getFileName(), ViewRenderable.builder().setView(context, R.layout.calendar_view).build());
            }
        }
    }

    /**
     * Called when the AugmentedImage is detected and should be rendered. A Sceneform node tree is
     * created based on an Anchor created from the image. The corners are then positioned based on the
     * extents of the image. There is no need to worry about world coordinates since everything is
     * relative to the center of the image, which is the parent node of the corners.
     */
    @SuppressWarnings({"AndroidApiChecker", "FutureReturnValueIgnored"})
    public void setImage(AugmentedImage image) {
        this.image = image;

        // If any of the models are not loaded, then recurse when all are loaded.
        CompletableFuture<ViewRenderable> calendarView = viewMap.get(image.getName());
        if (!calendarView.isDone()) {
            CompletableFuture.allOf(calendarView)
                    .thenAccept((Void aVoid) -> setImage(image))
                    .exceptionally(
                            throwable -> {
                                Log.e(TAG, "Exception loading", throwable);
                                return null;
                            });
        }

        ViewRenderable now = calendarView.getNow(null);
        if (now != null) {
            LinearLayout layout = (LinearLayout) now.getView();
            layout.setMinimumWidth(40);
            TextView tittle = layout.findViewById(R.id.tittle);
            tittle.setText(MeetingRoomManager.Manager.meetingRooms.get(image.getName()).getName());

            List<MeetingData> fakeMeetingData;

            if (image.getName().equals(MeetingRoomsConstants.PortlandRoom.FILE_NAME)) {
                fakeMeetingData = MeetingRoomManager.Manager.getFakeData(true);
            } else {
                fakeMeetingData = MeetingRoomManager.Manager.getFakeData(false);
            }

            String startAt = START_DAY_AT;
            for (MeetingData data : fakeMeetingData) {
                if (startAt.equals(data.getStart())){
                    layout.addView(MeetingRoomManager.Manager.buildBusyCalendarTextView(layout, data.getStart() + " - " + data.getEnd() + " (" + data.getOrganizer() + ")"));
                } else {
                    layout.addView(MeetingRoomManager.Manager.buildEmptyCalendarTextView(layout));
                    layout.addView(MeetingRoomManager.Manager.buildBusyCalendarTextView(layout, data.getStart() + " - " + data.getEnd() + " (" + data.getOrganizer() + ")"));
                }
                startAt = data.getEnd();
            }

            if (!END_DAY_AT.equals(startAt)){
                layout.addView(MeetingRoomManager.Manager.buildEmptyCalendarTextView(layout));
            }

        }

        // Set the anchor based on the center of the image.
        setAnchor(image.createAnchor(image.getCenterPose()));

        // Make the 4 corner nodes.
        Vector3 localPosition = new Vector3();
        localPosition.set(0.0f, 0.0f, 0.0f);

        Node viewNode = new Node();
        viewNode.setParent(this);
        viewNode.setLocalPosition(localPosition);
        viewNode.setRenderable(now);
    }

    public AugmentedImage getImage() {
        return image;
    }
}