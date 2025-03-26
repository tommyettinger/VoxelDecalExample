package gdx.liftoff.game;

import com.badlogic.gdx.graphics.g2d.Sprite;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.graphics.g2d.Animation;
import com.badlogic.gdx.graphics.g3d.decals.Decal;
import com.badlogic.gdx.graphics.g3d.decals.DecalBatch;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.math.collision.BoundingBox;
import com.badlogic.gdx.utils.Array;
import gdx.liftoff.IsoEngine3D;

public class Player {
    public Vector3 position;
    private Vector3 velocity;
    private boolean isGrounded;
    private LocalMap map;

    private Array<Array<Animation<Sprite>>> animations;
    public final int playerId;
    private float stateTime;
    private int currentDirection;

    private static final float GRAVITY = -0.5f; // multiplied by delta, which is expected to be about 1f/60f
    private static final float MAX_GRAVITY = -0.15f;
    private static final float JUMP_FORCE = 0.25f;
    private static final float MOVE_SPEED = 0.03f;
    private static final float PLAYER_SIZE = 1f;

    private Decal playerDecal;

    public Player(LocalMap map, Array<Array<Animation<Sprite>>> animations, int playerId) {
        this.map = map;
        this.position = new Vector3(0f, 5f, 2f);
        this.velocity = new Vector3(0, 0, 0);
        this.stateTime = 0;
        this.currentDirection = 0; // Default: facing down
        this.playerId = playerId;

        this.animations = animations;

        // Initialize the decal
        playerDecal = Decal.newDecal(1f, 1f, animations.get(currentDirection).get(playerId).getKeyFrame(stateTime), true);
    }

    public void update(float deltaTime) {
        stateTime += deltaTime;

        applyGravity(deltaTime);
        position.add(velocity);
        handleCollision();

        // Update animation frame
        TextureRegion currentFrame;
        if (velocity.x != 0 || velocity.z != 0) {
            currentFrame = animations.get(currentDirection).get(playerId).getKeyFrame(stateTime, true);
        } else {
            /* The "currentDirection + 2" gets an attack animation instead of an idle one for the appropriate facing. */
            currentFrame = animations.get(currentDirection + 2).get(playerId).getKeyFrame(stateTime, true);
        }

        // Update decal
        playerDecal.setTextureRegion(currentFrame);
        playerDecal.setPosition(position);
        playerDecal.setRotation(IsoEngine3D.getInstance().camera.direction, IsoEngine3D.getInstance().camera.up); // Billboard effect
    }

    public void render(DecalBatch batch) {
        batch.add(playerDecal);
    }

    private void applyGravity(float delta) {
        if (!isGrounded) {
            velocity.y = Math.max(velocity.y + GRAVITY * delta, MAX_GRAVITY); // Apply gravity to Y (not Z)
        }
    }

    public void jump() {
        if (isGrounded) {
            velocity.y = JUMP_FORCE; // Jump should affect Y (height)
//            position.y += .1f;
            isGrounded = false;
        }
    }

    public void move(float dx, float dz) {
        boolean movingDiagonally = (dx != 0 && dz != 0);

        if (movingDiagonally) {
            // Scale movement based on tile proportions
            dx *= IsoEngine3D.TILE_RATIO;  // Scale x-movement to match tile proportions
            dz *= 1f;          // Keep z-movement unchanged (height already accounts for it)

            // Normalize to maintain consistent movement speed
            float length = (float) Math.sqrt(dx * dx + dz * dz);
            dx /= length;
            dz /= length;
        }

        velocity.x = dx * MOVE_SPEED;
        velocity.z = dz * MOVE_SPEED;

        if (dx == 0 && dz == 0) return;

        // Determine direction based on movement
        if (dz > 0) currentDirection = 1; // Up
        else currentDirection = 0; // Down
    }

    private void handleCollision() {
        isGrounded = false;

        // bottom of map
        float groundLevel = 1f;
        if (position.y < groundLevel) {
            position.y = groundLevel;
            velocity.y = 0;
            isGrounded = true;
        }

        BoundingBox playerBox = new BoundingBox(
            new Vector3(position.x - PLAYER_SIZE / 2, position.y, position.z - PLAYER_SIZE / 2),
            new Vector3(position.x + PLAYER_SIZE / 2, position.y + PLAYER_SIZE, position.z + PLAYER_SIZE / 2)
        );
        BoundingBox blockBox = new BoundingBox();

        for (int f = -1; f <= 1; f++) {
            for (int g = -1; g <= 1; g++) {
                for (int h = -1; h <= 1; h++) {
                    if (map.getTile(position.x + f, position.y + g, position.z + h) != -1) {
                        blockBox.min.set(position.x + f, position.y + g, position.z + h);
                        blockBox.max.set(position.x + f + 1, position.y + g + 1, position.z + h + 1);
                        if (playerBox.intersects(blockBox)) {
                            if (position.y > g) { // Check if falling onto a tile
                                position.y = g + 1; // Snap player to tile height
                                velocity.y = 0;
                                isGrounded = true;
                            } else { // tile collision from the side
                                velocity.x = 0;
                                velocity.z = 0;
                            }
                        }
                    }
                }
            }
        }
    }
}
