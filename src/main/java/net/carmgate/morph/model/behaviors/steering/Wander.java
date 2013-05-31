package net.carmgate.morph.model.behaviors.steering;

import net.carmgate.morph.model.Model;
import net.carmgate.morph.model.behaviors.ActivatedMorph;
import net.carmgate.morph.model.behaviors.Movement;
import net.carmgate.morph.model.behaviors.Needs;
import net.carmgate.morph.model.common.Vect3D;
import net.carmgate.morph.model.entities.Morph.MorphType;
import net.carmgate.morph.model.entities.Ship;

import org.lwjgl.opengl.GL11;
import org.newdawn.slick.opengl.TextureImpl;

@Needs({ @ActivatedMorph(morphType = MorphType.SIMPLE_PROPULSOR) })
public class Wander extends Movement {

	private static final int nbSegments = 200;
	private static final double deltaAngle = (float) (2 * Math.PI / nbSegments);
	private static final float cos = (float) Math.cos(deltaAngle);
	private static final float sin = (float) Math.sin(deltaAngle);

	private final float wanderFocusDistance;
	private final float wanderRadius;
	private final Vect3D steeringForce = new Vect3D();

	private final Vect3D wanderTarget = new Vect3D();
	private final Vect3D wanderFocus = new Vect3D();

	/**
	 * Do not use.
	 */
	@Deprecated
	public Wander() {
		wanderFocusDistance = 0;
		wanderRadius = 0;
	}

	public Wander(Ship shipToMove, float wanderFocusDistance, float wanderRadius) {
		super(shipToMove);
		this.wanderFocusDistance = wanderFocusDistance;
		this.wanderRadius = wanderRadius;
	}

	@Override
	public Vect3D getSteeringForce() {
		return steeringForce;
	}

	@Override
	public void initRenderer() {
		// nothing to do
	}

	@Override
	public boolean isActive() {
		return wanderFocusDistance != 0;
	}

	@Override
	public void render(int glMode) {
		final Vect3D pos = shipToMove.getPos();
		final Vect3D speed = shipToMove.getSpeed();

		if (Model.getModel().getUiContext().isDebugMode()) {
			GL11.glTranslatef(pos.x, pos.y, pos.z);
			speed.render(1);
			GL11.glColor3f(0, 0, 1);
			steeringForce.render(1);

			// render limit of effect zone
			GL11.glBegin(GL11.GL_LINES);
			float t = 0; // temporary data holder
			float x = wanderFocusDistance; // radius
			float y = 0;
			for (int i = 0; i < nbSegments; i++) {
				GL11.glColor4d(1, 1, 1, 0.15);
				GL11.glVertex2d(x, y);

				t = x;
				x = cos * x - sin * y;
				y = sin * t + cos * y;

				GL11.glVertex2d(x, y);
			}
			GL11.glEnd();

			GL11.glTranslatef(-pos.x, -pos.y, -pos.z);

			GL11.glTranslatef(wanderFocus.x, wanderFocus.y, 0);

			// render limit of effect zone
			GL11.glBegin(GL11.GL_LINES);
			t = 0; // temporary data holder
			x = wanderRadius; // radius
			y = 0;
			for (int i = 0; i < nbSegments; i++) {
				GL11.glColor4d(1, 1, 1, 0.15);
				GL11.glVertex2d(x, y);

				t = x;
				x = cos * x - sin * y;
				y = sin * t + cos * y;

				GL11.glVertex2d(x, y);
			}
			GL11.glEnd();

			GL11.glTranslatef(wanderTarget.x, wanderTarget.y, 0);

			GL11.glColor4f(1, 1, 1, 1);
			TextureImpl.bindNone();
			GL11.glBegin(GL11.GL_QUADS);
			GL11.glVertex2f(-16, -16);
			GL11.glVertex2f(16, -16);
			GL11.glVertex2f(16, 16);
			GL11.glVertex2f(-16, 16);
			GL11.glEnd();

			GL11.glTranslatef(-wanderTarget.x, -wanderTarget.y, 0);
			GL11.glTranslatef(-wanderFocus.x, -wanderFocus.y, 0);
		}

	}

	@Override
	public void run(float secondsSinceLastUpdate) {
		if (wanderRadius == 0) {
			shipToMove.removeBehavior(this);
			return;
		}

		final Vect3D pos = new Vect3D(shipToMove.getPos());

		wanderFocus.copy(pos)
				.add(new Vect3D(Vect3D.NORTH).rotate(shipToMove.getHeading()).normalize(wanderFocusDistance + shipToMove.getMass()));

		// Determine a target at acceptable distance from the wander focus point
		wanderTarget.x += Math.random() * 0.25f - 0.125f;
		wanderTarget.y += Math.random() * 0.25f - 0.125f;
		if (new Vect3D(wanderFocus).add(wanderTarget).distance(wanderFocus) > wanderRadius) {
			wanderTarget.copy(Vect3D.NULL);
		}

		steeringForce.copy(new Vect3D(wanderFocus).substract(pos).add(wanderTarget)).truncate(shipToMove.getMaxSteeringForce() / shipToMove.getMass());
	}

}