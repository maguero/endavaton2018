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
import com.hackaton.endava.calendar.connect.GraphServiceController;
import com.hackaton.endava.calendar.model.MeetingData;
import com.hackaton.endava.calendar.model.MeetingRoom;
import com.microsoft.graph.concurrency.ICallback;
import com.microsoft.graph.core.ClientException;
import com.microsoft.graph.extensions.Event;
import com.microsoft.graph.extensions.IEventCollectionPage;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public class AugmentedImageNode extends AnchorNode {

    private static final String TAG = "AugmentedImageNode";
    final private GraphServiceController mGraphServiceController = new GraphServiceController();

    // The augmented image represented by this node.
    private AugmentedImage image;

    private static Map<String, CompletableFuture<ViewRenderable>> viewMap =
            new HashMap<String, CompletableFuture<ViewRenderable>>();


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

        // Set the anchor based on the center of the image.
        setAnchor(image.createAnchor(image.getCenterPose()));

        // Make the 4 corner nodes.
        Vector3 localPosition = new Vector3();
        localPosition.set(0.0f, 0.0f, 0.0f);

        Node viewNode = new Node();
        viewNode.setParent(this);

        mGraphServiceController.getCalendarById(MeetingRoomManager.Manager.meetingRooms.get(image.getName()).getCalendarId(), new ICallback<IEventCollectionPage>() {
                    @Override
                    public void success(IEventCollectionPage iEventCollectionPage) {

                        List<MeetingData> meetingDataList = new ArrayList<MeetingData>();
                        List<Event> eventList = iEventCollectionPage.getCurrentPage();

                        for (Event event : eventList) {

                            MeetingData md = new MeetingData();
                            md.setOrganizer(event.organizer.emailAddress.name);

                            md.setStart(event.start.dateTime);
                            md.setEnd(event.end.dateTime);

                            meetingDataList.add(md);
                        }


                        // --- start drawing ---

                        if (now != null) {
                            LinearLayout layout = (LinearLayout) now.getView();
                            TextView tittle = layout.findViewById(R.id.tittle);
                            tittle.setText(MeetingRoomManager.Manager.meetingRooms.get(image.getName()).getName());

                            for (MeetingData data : meetingDataList) {
                                layout.addView(MeetingRoomManager.Manager.buildBusyCalendarTextView(layout, data.getStart() + " - " + data.getEnd() + " (" + data.getOrganizer() + ")"));
                            }
                        }

                        // --- end drawing ---

                        viewNode.setLocalPosition(localPosition);
                        viewNode.setRenderable(now);


                    }

                    @Override
                    public void failure(ClientException ex) {
                        // TODO complete behaviour
                    }

                });


    }

    public AugmentedImage getImage() {
        return image;
    }
}