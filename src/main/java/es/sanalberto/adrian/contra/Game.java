package es.sanalberto.adrian.contra;

import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.image.Image;
import javafx.scene.input.KeyCode;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.TextAlignment;
import org.jbox2d.callbacks.ContactImpulse;
import org.jbox2d.callbacks.ContactListener;
import org.jbox2d.collision.Manifold;
import org.jbox2d.collision.shapes.CircleShape;
import org.jbox2d.collision.shapes.PolygonShape;
import org.jbox2d.common.Vec2;
import org.jbox2d.dynamics.Body;
import org.jbox2d.dynamics.BodyDef;
import org.jbox2d.dynamics.BodyType;
import org.jbox2d.dynamics.Fixture;
import org.jbox2d.dynamics.FixtureDef;
import org.jbox2d.dynamics.World;
import org.jbox2d.dynamics.contacts.Contact;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

public class Game implements ContactListener {

    public static final int W = 960;
    public static final int H = 540;

    private static final float PPM = 32f;
    private static final float DT = 1f / 60f;
    private static final float LEVEL_W = 120f;
    private static final float KILL_Y = -12f;

    private final Canvas canvas;
    private World world;
    private Player player;

    private final List<Enemy> enemies = new ArrayList<>();
    private final List<Bullet> bullets = new ArrayList<>();
    private final List<StaticPiece> statics = new ArrayList<>();
    private final Set<KeyCode> keys = EnumSet.noneOf(KeyCode.class);

    private float accumulator = 0f;
    private float cameraX = 0f;
    private int score = 0;
    private boolean gameOver = false;
    private float spawnTimer = 1f;

    private Image playerImage;
    private Image enemyImage;

    public Game(Canvas canvas) {
        this.canvas = canvas;
        try {
            var playerStream = getClass().getResourceAsStream("/player.png");
            if (playerStream != null) {
                playerImage = new Image(playerStream);
            }
            var enemyStream = getClass().getResourceAsStream("/enemy.png");
            if (enemyStream != null) {
                enemyImage = new Image(enemyStream);
            }
        } catch (Exception ignored) {
        }
        initWorld();
    }

    private void initWorld() {
        world = new World(new Vec2(0f, -22f));
        world.setContactListener(this);

        enemies.clear();
        bullets.clear();
        statics.clear();
        keys.clear();

        // Suelo principal
        createStaticBox(LEVEL_W / 2f, -0.5f, LEVEL_W / 2f, 0.5f, true, false);

        // Plataformas ajustadas a alturas alcanzables (salto máximo aprox. 2.6m)
        createStaticBox(8f, 1.8f, 2f, 0.25f, false, false);
        createStaticBox(14f, 2.2f, 2.5f, 0.25f, false, false);
        createStaticBox(22f, 2.0f, 3f, 0.25f, false, false);
        createStaticBox(32f, 2.3f, 2.5f, 0.25f, false, false);
        createStaticBox(42f, 2.0f, 3.5f, 0.25f, false, false);
        createStaticBox(54f, 2.4f, 2.5f, 0.25f, false, false);
        createStaticBox(66f, 2.1f, 3f, 0.25f, false, false);
        createStaticBox(80f, 2.3f, 3f, 0.25f, false, false);
        createStaticBox(96f, 2.0f, 4f, 0.25f, false, false);

        // Paredes invisibles
        createStaticBox(-0.5f, 20f, 0.5f, 40f, false, true);
        createStaticBox(LEVEL_W + 0.5f, 20f, 0.5f, 40f, false, true);

        player = new Player(world, new Vec2(3f, 2f));

        accumulator = 0f;
        cameraX = 0f;
        score = 0;
        gameOver = false;
        spawnTimer = 1f;
    }

    public void keyPressed(KeyCode code) {
        keys.add(code);

        if (gameOver) {
            if (code == KeyCode.R) {
                initWorld();
            }
            return;
        }

        if (player != null && isJump(code)) {
            player.wantJump = true;
        }
    }

    public void keyReleased(KeyCode code) {
        keys.remove(code);
    }

    public void update(double dt) {
        if (gameOver) {
            return;
        }

        accumulator += (float) Math.min(dt, 0.05d);

        while (accumulator >= DT) {
            fixedUpdate(DT);
            accumulator -= DT;

            if (gameOver) {
                accumulator = 0f;
                break;
            }
        }
    }

    private void fixedUpdate(float dt) {
        handlePlayerInput(dt);

        spawnTimer -= dt;
        if (spawnTimer <= 0f && enemies.size() < 10) {
            spawnEnemy();
            spawnTimer = 1.35f;
        }

        for (Enemy enemy : enemies) {
            enemy.update(this, dt);
        }

        for (Bullet bullet : bullets) {
            bullet.update(this, dt);
        }

        if (player != null && !player.dead && player.body != null && player.y() < KILL_Y) {
            player.fallDeath(this);
        }

        world.step(DT, 8, 3);

        cleanup();
        updateCamera();
    }

    private void handlePlayerInput(float dt) {
        if (player == null || player.dead || player.body == null) {
            return;
        }

        boolean left = pressedLeft();
        boolean right = pressedRight();

        float dir = 0f;
        if (left && !right) {
            dir = -1f;
        } else if (right && !left) {
            dir = 1f;
        }

        player.move(dir, pressedShoot(), dt, this);
    }

    private boolean pressedLeft() {
        return keys.contains(KeyCode.LEFT) || keys.contains(KeyCode.A);
    }

    private boolean pressedRight() {
        return keys.contains(KeyCode.RIGHT) || keys.contains(KeyCode.D);
    }

    private boolean pressedShoot() {
        return keys.contains(KeyCode.CONTROL) || keys.contains(KeyCode.J) || keys.contains(KeyCode.X);
    }

    private boolean isJump(KeyCode code) {
        return code == KeyCode.SPACE || code == KeyCode.UP || code == KeyCode.W;
    }

    private void spawnEnemy() {
        float x = cameraX / PPM + W / PPM + 1f;
        x = Math.min(LEVEL_W - 2f, x);
        enemies.add(new Enemy(world, new Vec2(x, 3f)));
    }

    private void spawnBullet(Player p) {
        if (p.body == null) {
            return;
        }

        Vec2 pos = new Vec2(p.x() + p.facing * 0.6f, p.y() + 0.15f);
        bullets.add(new Bullet(world, pos, p.facing));
    }

    private void cleanup() {
        for (Iterator<Bullet> it = bullets.iterator(); it.hasNext(); ) {
            Bullet b = it.next();

            if (b.dead || b.body == null) {
                destroyEntity(b);
                it.remove();
            }
        }

        for (Iterator<Enemy> it = enemies.iterator(); it.hasNext(); ) {
            Enemy e = it.next();

            if (e.dead || (e.body != null && e.y() < KILL_Y)) {
                destroyEntity(e);
                it.remove();
            }
        }
    }

    private void destroyEntity(Entity entity) {
        if (entity.body != null) {
            world.destroyBody(entity.body);
            entity.body = null;
        }
        entity.dead = true;
    }

    private void updateCamera() {
        if (player == null || player.body == null) {
            return;
        }

        float target = player.x() * PPM - W / 2f;
        cameraX = Math.max(0f, Math.min(target, LEVEL_W * PPM - W));
    }

    private void createStaticBox(float x, float y, float halfW, float halfH, boolean ground, boolean wall) {
        BodyDef bd = new BodyDef();
        bd.type = BodyType.STATIC;
        bd.position.set(x, y);

        Body body = world.createBody(bd);

        PolygonShape shape = new PolygonShape();
        shape.setAsBox(halfW, halfH);

        FixtureDef fd = new FixtureDef();
        fd.shape = shape;
        fd.friction = 0.9f;
        fd.restitution = 0f;

        Fixture fixture = body.createFixture(fd);
        fixture.setUserData(wall ? "WALL" : "GROUND");

        statics.add(new StaticPiece(body, halfW, halfH, ground, wall));
    }

    public void render() {
        GraphicsContext g = canvas.getGraphicsContext2D();

        g.setFill(Color.rgb(10, 14, 24));
        g.fillRect(0, 0, W, H);

        drawBackground(g);
        drawStatics(g);

        for (Bullet b : bullets) {
            drawBullet(g, b);
        }

        for (Enemy e : enemies) {
            drawEnemy(g, e);
        }

        drawPlayer(g);
        drawHud(g);
    }

    private void drawBackground(GraphicsContext g) {
        g.setFill(Color.rgb(60, 75, 110, 0.7));

        for (int i = 0; i < 45; i++) {
            float x = (i * 137f) % W;
            float y = (i * 89f) % (H - 180f);
            g.fillRect(x, y, 2, 2);
        }
    }

    private void drawStatics(GraphicsContext g) {
        for (StaticPiece s : statics) {
            if (s.wall) {
                continue;
            }

            Vec2 p = s.body.getPosition();

            float cx = toScreenX(p.x);
            float cy = toScreenY(p.y);

            float w = s.halfW * 2f * PPM;
            float h = s.halfH * 2f * PPM;

            g.setFill(s.ground ? Color.web("#31424a") : Color.web("#4f6b3a"));
            g.fillRect(cx - w / 2f, cy - h / 2f, w, h);

            g.setFill(s.ground ? Color.web("#5d7c87") : Color.web("#89a85a"));
            g.fillRect(cx - w / 2f, cy - h / 2f, w, 3f);
        }
    }

    private void drawPlayer(GraphicsContext g) {
        if (player == null || player.body == null) {
            return;
        }

        boolean blink = player.invuln > 0f && ((int) (player.invuln * 12f) % 2 == 0);

        float cx = toScreenX(player.x());
        float cy = toScreenY(player.y());

        float w = player.widthM * PPM;
        float h = player.heightM * PPM;

        if (!blink && !player.dead) {
            if (playerImage != null) {
                // If facing left, flip horizontally
                if (player.facing < 0) {
                    g.drawImage(playerImage, cx + w / 2f, cy - h / 2f, -w, h);
                } else {
                    g.drawImage(playerImage, cx - w / 2f, cy - h / 2f, w, h);
                }
            } else {
                g.setFill(Color.rgb(215, 70, 70));
                g.fillRect(cx - w / 2f, cy - h / 2f, w, h);

                g.setFill(Color.rgb(245, 225, 190));
                g.fillRect(cx - w / 4f, cy - h / 2f + 4f, w / 2f, h / 4f);

                g.setStroke(Color.LIGHTGRAY);
                g.setLineWidth(3f);
                double gunY = toScreenY(player.y() + 0.12f);
                g.strokeLine(cx, gunY, cx + player.facing * 24f, gunY);
                g.setLineWidth(1f);
            }
        }
    }

    private void drawEnemy(GraphicsContext g, Enemy e) {
        if (e.body == null || e.dead) {
            return;
        }

        float cx = toScreenX(e.x());
        float cy = toScreenY(e.y());

        float w = e.widthM * PPM;
        float h = e.heightM * PPM;

        if (enemyImage != null) {
            float dir = -1f;
            if (player != null && !player.dead && player.body != null) {
                dir = player.x() > e.x() ? 1f : -1f;
            }
            // Sprite base apunta a la izquierda por defecto
            if (dir > 0) {
                g.drawImage(enemyImage, cx + w / 2f, cy - h / 2f, -w, h);
            } else {
                g.drawImage(enemyImage, cx - w / 2f, cy - h / 2f, w, h);
            }
        } else {
            g.setFill(Color.rgb(95, 180, 95));
            g.fillRect(cx - w / 2f, cy - h / 2f, w, h);

            g.setFill(Color.rgb(25, 65, 25));
            g.fillRect(cx - w / 4f, cy - h / 2f + 5f, w / 2f, 5f);
        }
    }

    private void drawBullet(GraphicsContext g, Bullet b) {
        if (b.body == null || b.dead) {
            return;
        }

        float cx = toScreenX(b.x());
        float cy = toScreenY(b.y());
        float r = b.widthM * PPM / 2f;

        g.setFill(Color.YELLOW);
        g.fillOval(cx - r, cy - r, r * 2f, r * 2f);
    }

    private void drawHud(GraphicsContext g) {
        g.setTextAlign(TextAlignment.LEFT);
        g.setFill(Color.WHITE);
        g.setFont(Font.font("Verdana", 16));

        int hp = player == null ? 0 : Math.max(0, player.health);

        g.fillText("VIDA: " + hp, 16, 28);
        g.fillText("PUNTOS: " + score, 16, 52);

        g.setFont(Font.font("Verdana", 12));
        g.fillText("A/D o flechas: mover | ESPACIO/W/Arriba: saltar | CTRL/J/X: disparar | R: reiniciar", 16, H - 14);

        if (gameOver) {
            g.setFill(Color.color(0, 0, 0, 0.55));
            g.fillRect(0, 0, W, H);

            g.setFill(Color.WHITE);
            g.setTextAlign(TextAlignment.CENTER);

            g.setFont(Font.font("Verdana", 36));
            g.fillText("GAME OVER", W / 2.0, H / 2.0 - 12);

            g.setFont(Font.font("Verdana", 18));
            g.fillText("Pulsa R para reiniciar", W / 2.0, H / 2.0 + 28);

            g.setTextAlign(TextAlignment.LEFT);
        }
    }

    private float toScreenX(float worldX) {
        return worldX * PPM - cameraX;
    }

    private float toScreenY(float worldY) {
        return H - worldY * PPM;
    }

    @Override
    public void beginContact(Contact contact) {
        Fixture fa = contact.getFixtureA();
        Fixture fb = contact.getFixtureB();

        handleFoot(fa, fb, true);
        handleFoot(fb, fa, true);

        Entity a = entityOf(fa);
        Entity b = entityOf(fb);

        if (a instanceof Bullet) {
            handleBulletHit((Bullet) a, b, fb.getBody());
        }

        if (b instanceof Bullet) {
            handleBulletHit((Bullet) b, a, fa.getBody());
        }

        if (a instanceof Player && b instanceof Enemy) {
            ((Player) a).hurt(this);
        }

        if (b instanceof Player && a instanceof Enemy) {
            ((Player) b).hurt(this);
        }
    }

    @Override
    public void endContact(Contact contact) {
        handleFoot(contact.getFixtureA(), contact.getFixtureB(), false);
        handleFoot(contact.getFixtureB(), contact.getFixtureA(), false);
    }

    @Override
    public void preSolve(Contact contact, Manifold oldManifold) {
    }

    @Override
    public void postSolve(Contact contact, ContactImpulse impulse) {
    }

    private void handleFoot(Fixture footFixture, Fixture otherFixture, boolean begin) {
        if (player == null || player.dead) {
            return;
        }

        if (!"FOOT".equals(footFixture.getUserData())) {
            return;
        }

        Body otherBody = otherFixture.getBody();
        if (otherBody == null || otherBody.getType() != BodyType.STATIC) {
            return;
        }

        if ("WALL".equals(otherFixture.getUserData())) {
            return;
        }

        if (begin) {
            player.groundContacts++;
        } else {
            player.groundContacts = Math.max(0, player.groundContacts - 1);
        }
    }

    private Entity entityOf(Fixture fixture) {
        if (fixture == null || fixture.getBody() == null) {
            return null;
        }

        Object userData = fixture.getBody().getUserData();
        return userData instanceof Entity ? (Entity) userData : null;
    }

    private void handleBulletHit(Bullet bullet, Entity other, Body otherBody) {
        if (bullet == null || bullet.dead) {
            return;
        }

        if (other instanceof Enemy) {
            if (!other.dead) {
                score += 100;
            }

            other.dead = true;
            bullet.dead = true;
            return;
        }

        if (otherBody != null && otherBody.getType() == BodyType.STATIC) {
            bullet.dead = true;
        }
    }

    private enum EntityType {
        PLAYER,
        ENEMY,
        BULLET
    }

    private static class Entity {
        final EntityType type;
        Body body;
        boolean dead;
        final float widthM;
        final float heightM;

        Entity(EntityType type, float widthM, float heightM) {
            this.type = type;
            this.widthM = widthM;
            this.heightM = heightM;
        }

        float x() {
            return body.getPosition().x;
        }

        float y() {
            return body.getPosition().y;
        }
    }

    private static class Player extends Entity {
        int health = 3;
        int facing = 1;

        float invuln = 0f;
        float shootCooldown = 0f;

        int groundContacts = 0;
        boolean wantJump = false;

        Player(World world, Vec2 pos) {
            super(EntityType.PLAYER, 0.6f, 1.4f);

            BodyDef bd = new BodyDef();
            bd.type = BodyType.DYNAMIC;
            bd.position.set(pos.x, pos.y);
            bd.fixedRotation = true;

            body = world.createBody(bd);

            PolygonShape shape = new PolygonShape();
            shape.setAsBox(widthM / 2f, heightM / 2f);

            FixtureDef fd = new FixtureDef();
            fd.shape = shape;
            fd.density = 1f;
            fd.friction = 0.05f;
            fd.restitution = 0f;

            body.createFixture(fd);

            PolygonShape footShape = new PolygonShape();
            footShape.setAsBox(
                    widthM * 0.45f,
                    0.05f,
                    new Vec2(0f, -heightM / 2f - 0.03f),
                    0f
            );

            FixtureDef footDef = new FixtureDef();
            footDef.shape = footShape;
            footDef.isSensor = true;
            footDef.density = 0f;

            Fixture footFixture = body.createFixture(footDef);
            footFixture.setUserData("FOOT");

            body.setUserData(this);
        }

        void move(float dir, boolean shoot, float dt, Game gamekk) {
            if (dead || body == null) {
                return;
            }

            invuln = Math.max(0f, invuln - dt);
            shootCooldown = Math.max(0f, shootCooldown - dt);

            if (dir < 0f) {
                facing = -1;
            } else if (dir > 0f) {
                facing = 1;
            }

            Vec2 vel = body.getLinearVelocity();
            body.setLinearVelocity(new Vec2(dir * 5f, vel.y));

            if (wantJump) {
                if (groundContacts > 0) {
                    Vec2 v = body.getLinearVelocity();
                    body.setLinearVelocity(new Vec2(v.x, 10.8f));
                }

                wantJump = false;
            }

            if (shoot && shootCooldown <= 0f) {
                gamekk.spawnBullet(this);
                shootCooldown = 0.18f;
            }
        }

        void hurt(Game gamekk) {
            if (dead || invuln > 0f) {
                return;
            }

            health--;
            invuln = 1.5f;

            if (health <= 0) {
                health = 0;
                dead = true;
                gamekk.gameOver = true;
            }
        }

        void fallDeath(Game gamekk) {
            if (dead) {
                return;
            }

            health = 0;
            dead = true;
            gamekk.gameOver = true;
        }
    }

    private static class Enemy extends Entity {
        private final float speed = 2.4f;

        Enemy(World world, Vec2 pos) {
            super(EntityType.ENEMY, 0.65f, 1.3f);

            BodyDef bd = new BodyDef();
            bd.type = BodyType.DYNAMIC;
            bd.position.set(pos.x, pos.y);
            bd.fixedRotation = true;

            body = world.createBody(bd);

            PolygonShape shape = new PolygonShape();
            shape.setAsBox(widthM / 2f, heightM / 2f);

            FixtureDef fd = new FixtureDef();
            fd.shape = shape;
            fd.density = 1f;
            fd.friction = 0.05f;
            fd.restitution = 0f;

            body.createFixture(fd);

            body.setUserData(this);
        }

        void update(Game gamekk, float dt) {
            if (dead || body == null) {
                return;
            }

            float dir = -1f;

            if (gamekk.player != null && !gamekk.player.dead && gamekk.player.body != null) {
                dir = gamekk.player.x() > x() ? 1f : -1f;
            }

            body.setLinearVelocity(new Vec2(dir * speed, body.getLinearVelocity().y));

            if (y() < KILL_Y) {
                dead = true;
            }
        }
    }

    private static class Bullet extends Entity {
        private float life = 1.1f;
        private final int facing;

        Bullet(World world, Vec2 pos, int facing) {
            super(EntityType.BULLET, 0.16f, 0.16f);
            this.facing = facing;

            BodyDef bd = new BodyDef();
            bd.type = BodyType.DYNAMIC;
            bd.position.set(pos.x, pos.y);
            bd.bullet = true;
            bd.fixedRotation = true;
            bd.gravityScale = 0f;

            body = world.createBody(bd);

            CircleShape shape = new CircleShape();
            shape.m_radius = 0.08f;

            FixtureDef fd = new FixtureDef();
            fd.shape = shape;
            fd.density = 0.01f;
            fd.friction = 0f;
            fd.restitution = 0f;
            fd.isSensor = true;

            body.createFixture(fd);
            body.setUserData(this);
            body.setLinearVelocity(new Vec2(facing * 18f, 0f));
        }

        void update(Game gamekk, float dt) {
            if (dead || body == null) {
                return;
            }

            life -= dt;

            body.setLinearVelocity(new Vec2(facing * 18f, 0f));

            if (life <= 0f || x() < -1f || x() > LEVEL_W + 1f) {
                dead = true;
            }
        }
    }

    private static class StaticPiece {
        final Body body;
        final float halfW;
        final float halfH;
        final boolean ground;
        final boolean wall;

        StaticPiece(Body body, float halfW, float halfH, boolean ground, boolean wall) {
            this.body = body;
            this.halfW = halfW;
            this.halfH = halfH;
            this.ground = ground;
            this.wall = wall;
        }
    }
}
