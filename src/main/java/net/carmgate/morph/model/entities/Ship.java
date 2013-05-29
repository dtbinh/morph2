package net.carmgate.morph.model.entities;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import net.carmgate.morph.model.Model;
import net.carmgate.morph.model.behaviors.Behavior;
import net.carmgate.morph.model.behaviors.ForceGeneratingBehavior;
import net.carmgate.morph.model.behaviors.Movement;
import net.carmgate.morph.model.behaviors.StarsGravityPull;
import net.carmgate.morph.model.common.Vect3D;
import net.carmgate.morph.model.entities.common.Entity;
import net.carmgate.morph.model.entities.common.EntityHints;
import net.carmgate.morph.model.entities.common.EntityType;
import net.carmgate.morph.model.entities.common.Renderable;
import net.carmgate.morph.model.entities.orders.Die;
import net.carmgate.morph.model.entities.orders.Order;
import net.carmgate.morph.model.entities.orders.TakeDamage;
import net.carmgate.morph.model.player.Player;
import net.carmgate.morph.model.player.Player.PlayerType;
import net.carmgate.morph.ui.rendering.RenderingHints;
import net.carmgate.morph.ui.rendering.RenderingSteps;

import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.collections.Predicate;
import org.apache.commons.collections.functors.NotPredicate;
import org.lwjgl.opengl.GL11;
import org.newdawn.slick.opengl.Texture;
import org.newdawn.slick.opengl.TextureImpl;
import org.newdawn.slick.opengl.TextureLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@EntityHints(entityType = EntityType.SHIP)
@RenderingHints(renderingStep = RenderingSteps.SHIP)
public class Ship extends Entity {

	private static final class SameClassPredicate implements Predicate {
		private final Class<?> behaviorClass;

		public SameClassPredicate(Class<?> behaviorClass) {
			this.behaviorClass = behaviorClass;
		}

		@Override
		public boolean evaluate(Object object) {
			return behaviorClass.isInstance(object);
		}
	}

	private static final int nbSegments = 200;
	private static final double deltaAngle = (float) (2 * Math.PI / nbSegments);
	private static final float cos = (float) Math.cos(deltaAngle);
	private static final float sin = (float) Math.sin(deltaAngle);

	private static final Logger LOGGER = LoggerFactory.getLogger(Ship.class);
	// Management of the ship's ids.
	private static Integer nextId = 1;

	private final int id;

	/** The texture under the morph image. */
	private static Texture baseTexture;
	private static Texture zoomedOutTexture;

	/** The ship max speed. */
	// IMPROVE All these values should depend on the ship's fitting.
	public static final float MAX_SPEED = 1000;
	public static final float MAX_FORCE = 3000f;
	private static final float MAX_ANGLE_SPEED_PER_MASS_UNIT = 3600f;
	private static final float MAX_DAMAGE = 10;
	public static final float MAX_RANGE = 300;
	private final List<Morph> morphs = new ArrayList<>();

	/** The ship position in the world. */
	private final Vect3D pos = new Vect3D();

	private final Vect3D speed = new Vect3D();

	/** The ship orientation in the world. */
	private float heading;

	private final List<Order> orderList = new ArrayList<>();

	private float mass = 10;

	/** Timestamp of last time the ship's position was calculated. */
	// IMPROVE We should move this in a class that can handle this behavior for any Updatable
	private long lastUpdateTS;

	private boolean selected;

	private final Vect3D accel = new Vect3D();

	private final Vect3D effectiveForce = new Vect3D();

	private float secondsSinceLastUpdate;
	private final Player player;
	private final Vect3D steeringForce = new Vect3D();

	private final Set<Behavior> behaviorSet = new HashSet<>();
	private final Set<Behavior> pendingRemovalBehaviors = new HashSet<>();

	private float damage = 0;

	/***
	 * Creates a new ship with position (0, 0, 0), mass = 10 assigned to player "self".
	 */
	public Ship() {
		this(0, 0, 0, 0, 10, Model.getModel().getSelf());
	}

	public Ship(float x, float y, float z, float heading, float mass, Player player) {
		this.player = player;
		Model.getModel().getPlayers().add(player);

		synchronized (nextId) {
			id = nextId++;
		}

		pos.copy(x, y, z);
		// speed.nullify();
		// accel = new Vect3D();
		// posAccel = new Vect3D(0, 0, 0);
		this.heading = heading;
		this.mass = mass;
		// rotSpeed = 0;

		// Init lastUpdateTS
		lastUpdateTS = Model.getModel().getCurrentTS();

		// Add always active behaviors
		addBehavior(new StarsGravityPull(this));
	}

	public boolean addBehavior(Behavior e) {
		return behaviorSet.add(e);
	}

	private void addTrail() {
		if (steeringForce.modulus() > MAX_FORCE * 0.002) {
			Model.getModel()
					.getParticleEngine()
					.addParticle(new Vect3D(pos), new Vect3D().substract(new Vect3D(steeringForce).mult(2)), 1f, 1f / 32,
							steeringForce.modulus() / (MAX_FORCE / mass) * 0.2f, steeringForce.modulus() / (MAX_FORCE / mass) * 1f);
		}
	}

	// IMPROVE remove effectiveForce from steeringForce management ?
	// movements should add a propulsion force to the ship
	private void applySteeringForce(Vect3D force) {
		steeringForce.add(force);
		effectiveForce.add(force);
	}

	public void fireOrder(Order order) {
		orderList.add(order);
	}

	public Vect3D getAccel() {
		return accel;
	}

	public float getHeading() {
		return heading;
	}

	@Override
	public int getId() {
		return id;
	}

	public float getMass() {
		return mass;
	}

	public List<Morph> getMorphs() {
		return morphs;
	}

	public Vect3D getPos() {
		return pos;
	}

	public Vect3D getSpeed() {
		return speed;
	}

	/**
	 * This method handles orders.
	 * IMPROVE This probably should be improved. It is quite ugly to have such a if-else cascade.
	 * However, I don't want to use a handler factory that would kill the current simplicity of orders handling
	 * @param order
	 */
	private void handleOrder(Order order) {
		if (order instanceof TakeDamage) {
			// This is not multiplied by lastUpdateTS because the timing is handled by the sender of the event.
			damage += ((TakeDamage) order).getDamageAmount();
			if (damage > MAX_DAMAGE) {
				fireOrder(new Die());
			}
			LOGGER.debug("Damage at " + damage + " for " + this);
		} else if (order instanceof Die) {
			Model.getModel().removeEntity(this);
		}
	}

	/** List of ships IAs. */
	// private final List<IA> iaList = new ArrayList<IA>();

	@Override
	public void initRenderer() {
		// load texture from PNG file if needed
		if (baseTexture == null) {
			try (FileInputStream fileInputStream = new FileInputStream(ClassLoader.getSystemResource("spaceship.png").getPath())) {
				baseTexture = TextureLoader.getTexture("PNG", fileInputStream);
			} catch (IOException e) {
				LOGGER.error("Exception raised while loading texture", e);
			}
		}

		if (zoomedOutTexture == null) {
			try (FileInputStream fileInputStream = new FileInputStream(ClassLoader.getSystemResource("spaceshipZoomedOut.png").getPath())) {
				zoomedOutTexture = TextureLoader.getTexture("PNG", fileInputStream);
			} catch (IOException e) {
				LOGGER.error("Exception raised while loading texture", e);
			}
		}
	}

	@Override
	public boolean isSelected() {
		return selected;
	}

	private void processAI() {
		// TODO Outsource this AI to allow several kinds of AIs
		// TODO implement AI processing
		// Very simple AI : wander and attack

	}

	/**
	 * Removes a behavior from the ship's behavior collection.
	 * This method postpones the behavior deletion until the end of the processing loop.
	 * This way, the handling of behaviors is insensitive to the order in which they are processed and removed.
	 * @param behavior to remove
	 */
	public void removeBehavior(Behavior behavior) {
		pendingRemovalBehaviors.add(behavior);
	}

	/**
	 * Removes all the behaviors that are of the same type
	 * @param behaviorClass
	 */
	public void removeBehaviorsByClass(Class<?> behaviorClass) {
		if (behaviorClass == null) {
			LOGGER.error("This method parameter should not be null");
		}

		CollectionUtils.filter(behaviorSet, NotPredicate.getInstance(new SameClassPredicate(behaviorClass)));
	}

	@Override
	public void render(int glMode) {

		GL11.glTranslatef(pos.x, pos.y, pos.z);
		GL11.glRotatef(heading, 0, 0, 1);
		float massScale = mass / 10;

		// Render selection circle around the ship
		boolean maxZoom = 64f * massScale * Model.getModel().getViewport().getZoomFactor() > 15;
		if (selected) {
			// render limit of effect zone
			TextureImpl.bindNone();
			float tInt = 0; // temporary data holder
			float tExt = 0; // temporary data holder
			float xInt;
			float xExt;
			if (maxZoom) {
				xInt = 64 * massScale - 15; // radius
				xExt = 64 * massScale - 15 + 6 / Model.getModel().getViewport().getZoomFactor(); // radius
			} else {
				xInt = 15f / Model.getModel().getViewport().getZoomFactor(); // radius
				xExt = 21f / Model.getModel().getViewport().getZoomFactor(); // radius
			}
			float xIntBackup = xInt; // radius
			float xExtBackup = xExt; // radius
			float yInt = 0;
			float yExt = 0;
			float yIntBackup = 0;
			float yExtBackup = 0;
			float alphaMax = 1f;
			for (int i = 0; i < nbSegments; i++) {

				tInt = xInt;
				tExt = xExt;
				xInt = cos * xInt - sin * yInt;
				xExt = cos * xExt - sin * yExt;
				yInt = sin * tInt + cos * yInt;
				yExt = sin * tExt + cos * yExt;

				GL11.glBegin(GL11.GL_QUADS);
				GL11.glColor4f(0, 0.7f, 0, 0);
				GL11.glVertex2f(xInt, yInt);
				GL11.glColor4f(0, 0.7f, 0, 0);
				GL11.glVertex2f(xIntBackup, yIntBackup);
				GL11.glColor4f(0, 0.7f, 0, alphaMax);
				GL11.glVertex2f((xExtBackup + xIntBackup) / 2, (yExtBackup + yIntBackup) / 2);
				GL11.glColor4f(0, 0.7f, 0, alphaMax);
				GL11.glVertex2f((xExt + xInt) / 2, (yExt + yInt) / 2);
				GL11.glColor4f(0, 0.7f, 0, alphaMax);
				GL11.glVertex2f((xExtBackup + xIntBackup) / 2, (yExtBackup + yIntBackup) / 2);
				GL11.glColor4f(0, 0.7f, 0, alphaMax);
				GL11.glVertex2f((xExt + xInt) / 2, (yExt + yInt) / 2);
				GL11.glColor4f(0, 0.7f, 0, 0);
				GL11.glVertex2f(xExt, yExt);
				GL11.glColor4f(0, 0.7f, 0, 0);
				GL11.glVertex2f(xExtBackup, yExtBackup);
				GL11.glEnd();

				xIntBackup = xInt;
				xExtBackup = xExt;
				yIntBackup = yInt;
				yExtBackup = yExt;
			}
		}

		// Render for show
		if (Model.getModel().isDebugMode()) {
			// IMPROVE replace this with some more proper mass rendering
			float energyPercent = mass / 10;
			if (energyPercent <= 0) {
				GL11.glColor3f(0.1f, 0.1f, 0.1f);
			} else {
				GL11.glColor3f(1f - energyPercent, energyPercent, 0);
			}
		} else {
			GL11.glColor3f(1f, 1f, 1f);
		}
		if (maxZoom) {
			GL11.glScalef(massScale, massScale, 0);
			baseTexture.bind();
			GL11.glBegin(GL11.GL_QUADS);
			GL11.glTexCoord2f(0, 0);
			GL11.glVertex2f(-64, 64);
			GL11.glTexCoord2f(1, 0);
			GL11.glVertex2f(64, 64);
			GL11.glTexCoord2f(1, 1);
			GL11.glVertex2f(64, -64);
			GL11.glTexCoord2f(0, 1);
			GL11.glVertex2f(-64, -64);
			GL11.glEnd();
			GL11.glScalef(1 / massScale, 1 / massScale, 0);
		} else {
			float adjustedSize = 15 / Model.getModel().getViewport().getZoomFactor();
			zoomedOutTexture.bind();
			GL11.glBegin(GL11.GL_QUADS);
			GL11.glTexCoord2f(0, 0);
			GL11.glVertex2f(-adjustedSize, adjustedSize);
			GL11.glTexCoord2f(1, 0);
			GL11.glVertex2f(adjustedSize, adjustedSize);
			GL11.glTexCoord2f(1, 1);
			GL11.glVertex2f(adjustedSize, -adjustedSize);
			GL11.glTexCoord2f(0, 1);
			GL11.glVertex2f(-adjustedSize, -adjustedSize);
			GL11.glEnd();
		}

		GL11.glRotatef(-heading, 0, 0, 1);

		if (Model.getModel().isDebugMode()) {
			GL11.glColor3f(1, 1, 0);
			effectiveForce.render(glMode, 1);
		}

		GL11.glTranslatef(-pos.x, -pos.y, -pos.z);

		for (Behavior behavior : behaviorSet) {
			if (behavior instanceof Renderable) {
				((Renderable) behavior).render(glMode);
			}
		}

	}

	private void rotateProperly() {

		// if steeringForce is too small, we must not change the orientation or we will be
		// by orientation fluctuations due to improper angle approximation
		// LOGGER.debug("" + steeringForce.modulus());
		if (steeringForce.modulus() < 0.1) {
			return;
		}

		// rotate properly along the speed vector (historically along the steering force vector)
		float newHeading;
		float headingFactor = steeringForce.modulus() / MAX_FORCE * mass * 4;
		if (headingFactor > 3) {
			newHeading = new Vect3D(0, 1, 0).angleWith(steeringForce);
		} else if (headingFactor > 0) {
			newHeading = new Vect3D(0, 1, 0).angleWith(new Vect3D(steeringForce).mult(headingFactor).add(new Vect3D(speed).mult(1 - headingFactor / 3)));
		} else {
			newHeading = new Vect3D(0, 1, 0).angleWith(speed);
		}

		// heading = newHeading;
		float angleDiff = (newHeading - heading + 360) % 360;
		float maxAngleSpeed = MAX_ANGLE_SPEED_PER_MASS_UNIT / mass;
		if (angleDiff < maxAngleSpeed * Math.max(1, angleDiff / 180) * secondsSinceLastUpdate) {
			heading = newHeading;
		} else if (angleDiff < 180) {
			heading = heading + maxAngleSpeed * Math.max(1, angleDiff / 180) * secondsSinceLastUpdate;
		} else if (angleDiff >= 360 - maxAngleSpeed * Math.max(1, angleDiff / 180) * secondsSinceLastUpdate) {
			heading = newHeading;
		} else {
			heading = heading - maxAngleSpeed * Math.max(1, angleDiff / 180) * secondsSinceLastUpdate;
		}
	}

	@Override
	public void setSelected(boolean selected) {
		this.selected = selected;
	}

	@Override
	public String toString() {
		return "ship:" + pos.toString();
	}

	@Override
	public void update() {
		secondsSinceLastUpdate = ((float) Model.getModel().getCurrentTS() - lastUpdateTS) / 1000;
		lastUpdateTS = Model.getModel().getCurrentTS();
		if (secondsSinceLastUpdate == 0f) {
			return;
		}

		// TODO Is this really the proper way to do it ?
		accel.nullify();
		effectiveForce.nullify();
		steeringForce.nullify();

		// handle AI assignements if appropriate
		if (player.getPlayerType() == PlayerType.AI) {
			processAI();
		}

		// if no movement needed, no update needed
		for (Behavior behavior : behaviorSet) {
			if (behavior.isActive()) {
				behavior.run(secondsSinceLastUpdate);

				// if the behavior is a movement, use the generated steering force
				if (behavior instanceof Movement) {
					applySteeringForce(((Movement) behavior).getSteeringForce());
				}

				// if the behavior is generating a force, we must apply it
				if (behavior instanceof ForceGeneratingBehavior) {
					effectiveForce.add(((ForceGeneratingBehavior) behavior).getNonSteeringForce());
				}
			}
		}

		// rotate and add trail according to the steering force vector
		rotateProperly();
		addTrail();

		// acceleration = steering_force / mass
		accel.add(effectiveForce);
		// velocity = truncate (velocity + acceleration, max_speed)
		speed.add(new Vect3D(accel).mult(secondsSinceLastUpdate)).truncate(Ship.MAX_SPEED);
		// position = position + velocity
		pos.add(new Vect3D(speed).mult(secondsSinceLastUpdate));

		for (Order order : orderList) {
			handleOrder(order);
		}
		orderList.clear();

		// If the mass of the current ship is null or below, remove it
		if (mass <= 0) {
			Model.getModel().removeEntity(this);
		}

		// Cleaning
		for (Behavior behavior : pendingRemovalBehaviors) {
			behaviorSet.remove(behavior);
		}
	}
}
