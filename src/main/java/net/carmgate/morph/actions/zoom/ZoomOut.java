package net.carmgate.morph.actions.zoom;

import net.carmgate.morph.actions.common.Action;
import net.carmgate.morph.actions.common.ActionHints;
import net.carmgate.morph.actions.common.UIEvent;
import net.carmgate.morph.actions.common.UIEvent.EventType;
import net.carmgate.morph.conf.Conf;
import net.carmgate.morph.conf.Conf.ConfItem;
import net.carmgate.morph.model.Model;
import net.carmgate.morph.model.common.Vect3D;
import net.carmgate.morph.ui.GameMouse;
import net.carmgate.morph.ui.ViewPort;

import org.lwjgl.input.Keyboard;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ActionHints(mouseActionAutoload = true, keyboardActionAutoload = true)
public class ZoomOut implements Action {

	private static final Logger LOGGER = LoggerFactory.getLogger(ZoomOut.class);
	private static final float ZOOM_VARIATION = Conf.getFloatProperty(ConfItem.ZOOM_VARIATIONFACTOR);

	@Override
	public void run() {
		UIEvent lastEvent = Model.getModel().getInteractionStack().getLastEvent();
		if ((lastEvent.getButton() != Keyboard.KEY_DOWN
				|| lastEvent.getEventType() != EventType.KEYBOARD_UP)
				&& (lastEvent.getEventType() != EventType.MOUSE_WHEEL
				|| lastEvent.getButton() > 0)) {
			return;
		}

		ViewPort viewport = Model.getModel().getViewport();
		float zoomFactor = viewport.getZoomFactor();
		viewport.setZoomFactor(zoomFactor / ZOOM_VARIATION);
		Vect3D fromWindowCenterToMouse = new Vect3D(Model.getModel().getWindow().getWidth() / 2 - GameMouse.getX(),
				-Model.getModel().getWindow().getHeight() / 2 + GameMouse.getY(), 0);
		Model.getModel().getViewport().getFocalPoint().add(new Vect3D(fromWindowCenterToMouse).mult(ZOOM_VARIATION)).mult(1f / ZOOM_VARIATION)
				.substract(new Vect3D(fromWindowCenterToMouse).mult(1f / ZOOM_VARIATION));

		LOGGER.debug("Zoom factor: " + viewport.getZoomFactor());
	}

}
