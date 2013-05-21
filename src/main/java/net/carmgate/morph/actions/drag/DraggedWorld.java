package net.carmgate.morph.actions.drag;

import java.util.List;

import net.carmgate.morph.actions.common.Action;
import net.carmgate.morph.actions.common.ActionHints;
import net.carmgate.morph.model.Model;
import net.carmgate.morph.ui.Event;
import net.carmgate.morph.ui.Event.EventType;

@ActionHints(dragAction = true, mouseActionAutoload = true)
public class DraggedWorld implements Action {

	private final DragContext dragContext;

	public DraggedWorld() {
		dragContext = null;
	}

	public DraggedWorld(DragContext dragContext) {
		this.dragContext = dragContext;
	}

	@Override
	public void run() {
		List<Event> lastEvents = Model.getModel().getInteractionStack().getLastEvents(3);
		if (lastEvents.get(2).getEventType() != EventType.MOUSE_BUTTON_DOWN
				|| lastEvents.get(2).getButton() != 0
				|| lastEvents.get(1).getEventType() != EventType.MOUSE_MOVE
				|| lastEvents.get(0).getEventType() != EventType.MOUSE_BUTTON_UP) {
			return;
		}

		dragContext.reset();
	}

}
